/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.backends;

import static org.assertj.core.api.Assertions.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.replication.protocol.OperationContext.*;
import static org.opends.server.types.ResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import org.opends.server.TestCaseUtils;
import org.opends.server.backends.ChangelogBackend.SearchParams;
import org.opends.server.controls.ExternalChangelogRequestControl;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchListener;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.plugin.DomainFakeCfg;
import org.opends.server.replication.plugin.ExternalChangelogDomainFakeCfg;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyDnContext;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.je.ECLEnabledDomainPredicate;
import org.opends.server.replication.service.DSRSShutdownSync;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Attributes;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.Operation;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.TimeThread;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyDNOperation;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.Pair;

@SuppressWarnings("javadoc")
public class ChangelogBackendTestCase extends ReplicationTestCase
{
  private static final DebugTracer TRACER = getTracer();

  private static final String USER1_ENTRY_UUID = "11111111-1111-1111-1111-111111111111";
  private static final long CHANGENUMBER_ZERO = 0L;
  private static final int SERVER_ID_1 = 1201;
  private static final int SERVER_ID_2 = 1202;
  private static final String TEST_ROOT_DN_STRING2 = "o=test2";
  private static DN ROOT_DN_OTEST;
  private static DN ROOT_DN_OTEST2;

  private final int brokerSessionTimeout = 5000;
  private final int maxWindow = 100;

  /** The replicationServer that will be used in this test. */
  private ReplicationServer replicationServer;

  /** The port of the replicationServer. */
  private int replicationServerPort;

  /**
   * When used in a search operation, it includes all attributes (user and
   * operational)
   */
  private static final Set<String> ALL_ATTRIBUTES = newSet("*", "+");
  private static final List<Control> NO_CONTROL = null;

  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();
    ROOT_DN_OTEST = DN.decode(TEST_ROOT_DN_STRING);
    ROOT_DN_OTEST2 = DN.decode(TEST_ROOT_DN_STRING2);

    // This test suite depends on having the schema available.
    configureReplicationServer();
  }

  @Override
  @AfterClass
  public void classCleanUp() throws Exception
  {
    callParanoiaCheck = false;
    super.classCleanUp();

    remove(replicationServer);
    replicationServer = null;

    paranoiaCheck();
  }

  @AfterMethod
  public void clearReplicationDb() throws Exception
  {
    clearChangelogDB(replicationServer);
  }

  /** Configure a replicationServer for test. */
  private void configureReplicationServer() throws Exception
  {
    replicationServerPort = TestCaseUtils.findFreePort();

    ReplServerFakeConfiguration config = new ReplServerFakeConfiguration(
          replicationServerPort,
          "ChangelogBackendTestDB",
          replicationDbImplementation,
          0,         // purge delay
          71,        // server id
          0,         // queue size
          maxWindow, // window size
          null       // servers
    );
    config.setComputeChangeNumber(true);
    replicationServer = new ReplicationServer(config, new DSRSShutdownSync(), new ECLEnabledDomainPredicate()
    {
      @Override
      public boolean isECLEnabledDomain(DN baseDN)
      {
        return baseDN.equals(ROOT_DN_OTEST);
      }
    });
    debugInfo("configure", "ReplicationServer created:" + replicationServer);
  }

  /** Enable replication on provided domain DN and serverid, using provided port. */
  private Pair<ReplicationBroker, LDAPReplicationDomain> enableReplication(DN domainDN, int serverId,
      int replicationPort, int timeout) throws Exception
  {
    ReplicationBroker broker = openReplicationSession(domainDN, serverId, 100, replicationPort, timeout);
    DomainFakeCfg domainConf = newFakeCfg(domainDN, serverId, replicationPort);
    LDAPReplicationDomain replicationDomain = startNewReplicationDomain(domainConf, null, null);
    return Pair.of(broker, replicationDomain);
  }

  /** Start a new replication domain on the directory server side. */
  private LDAPReplicationDomain startNewReplicationDomain(
      DomainFakeCfg domainConf,
      SortedSet<String> eclInclude,
      SortedSet<String> eclIncludeForDeletes)
          throws Exception
  {
    domainConf.setExternalChangelogDomain(new ExternalChangelogDomainFakeCfg(true, eclInclude, eclIncludeForDeletes));
    // Set a Changetime heartbeat interval low enough
    // (less than default value that is 1000 ms)
    // for the test to be sure to consider all changes as eligible.
    domainConf.setChangetimeHeartbeatInterval(10);
    LDAPReplicationDomain newDomain = MultimasterReplication.createNewDomain(domainConf);
    newDomain.start();
    return newDomain;
  }

  private void removeReplicationDomains(LDAPReplicationDomain... domains)
  {
    for (LDAPReplicationDomain domain : domains)
    {
      if (domain != null)
      {
        domain.shutdown();
        MultimasterReplication.deleteDomain(domain.getBaseDN());
      }
    }
  }

  @Test(enabled=false)
  public void searchChangesOnOneSuffixUsingEmptyCookie() throws Exception
  {
    String testName = "FourChangesCookie";
    debugInfo(testName, "Starting test\n\n");

    CSN[] csns = generateAndPublishChangesForEachOperationType(testName);

    searchChangesForEachOperationTypeUsingEmptyCookie(csns, testName);

    assertChangelogAttributesInRootDSE(true, 1, 4);

    debugInfo(testName, "Ending search with success");
  }

  @Test(enabled=false)
  public void searchChangesOnOneSuffixUsingDraftMode() throws Exception
  {
    long firstChangeNumber = 1;
    String testName = "FourChanges/" + firstChangeNumber;
    debugInfo(testName, "Starting test\n\n");

    CSN[] csns = generateAndPublishChangesForEachOperationType(testName);

    searchChangesForEachOperationTypeUsingDraftMode(firstChangeNumber, csns, testName);

    assertChangelogAttributesInRootDSE(true, 1, 4);

    debugInfo(testName, "Ending search with success");
  }

  @Test(enabled=false)
  public void searchChangesOnOneSuffixMultipleTimesUsingDraftMode() throws Exception
  {
    replicationServer.getChangelogDB().setPurgeDelay(0);

    // write 4 changes starting from changenumber 1, and search them
    String testName = "Multiple/1";
    CSN[] csns = generateAndPublishChangesForEachOperationType(testName);
    searchChangesForEachOperationTypeUsingDraftMode(1, csns, testName);

    // write 4 more changes starting from changenumber 5, and search them
    testName = "Multiple/5";
    csns = generateAndPublishChangesForEachOperationType(testName);
    searchChangesForEachOperationTypeUsingDraftMode(5, csns, testName);

    // search from the provided change number: 6 (should be the add msg)
    CSN csnOfLastAddMsg = csns[1];
    searchChangelogForOneChangeNumber(6, csnOfLastAddMsg);

    // search from a provided change number interval: 5-7
    searchChangelogFromToChangeNumber(5,7);

    // check first and last change number
    assertChangelogAttributesInRootDSE(true, 1, 8);

    // add a new change, then check again first and last change number without previous search
    CSN csn = new CSN(TimeThread.getTime(), 10, SERVER_ID_1);
    publishChanges(testName, generateDeleteMsg(TEST_ROOT_DN_STRING, csn, testName, 1));

    assertChangelogAttributesInRootDSE(true, 1, 9);
  }

  /**
   * Verifies that is not possible to read the changelog without the changelog-read privilege
   */
  // TODO : enable when code is checking the privileges correctly
  @Test(enabled=false)
  public void searchingChangelogWithoutPrivilegeShouldFail() throws Exception
  {
    AuthenticationInfo nonPrivilegedUser = new AuthenticationInfo();

    InternalClientConnection conn = new InternalClientConnection(nonPrivilegedUser);
    InternalSearchOperation op = conn.processSearch("cn=changelog", SearchScope.WHOLE_SUBTREE, "(objectclass=*)");

    assertEquals(op.getResultCode(), ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    assertEquals(op.getErrorMessage().toMessage(), NOTE_SEARCH_CHANGELOG_INSUFFICIENT_PRIVILEGES.get());
  }

  /**
   * With an empty RS, a search should return only root entry.
   */
  @Test(enabled=false)
  public void searchWhenNoChangesShouldReturnRootEntryOnly() throws Exception
  {
    String testName = "EmptyRS";
    debugInfo(testName, "Starting test\n\n");

    searchChangelog("(objectclass=*)", 1, SUCCESS, testName);

    debugInfo(testName, "Ending test successfully");
  }

  @Test(enabled=false)
  public void searchWithUnknownChangeNumberShouldReturnNoResult() throws Exception
  {
    String testName = "UnknownChangeNumber";
    debugInfo(testName, "Starting test\n\n");

    searchChangelog("(changenumber=1000)", 0, SUCCESS, testName);

    debugInfo(testName, "Ending test with success");
  }

  @Test(enabled=false)
  public void operationalAndVirtualAttributesShouldNotBeVisibleOutsideRootDSE() throws Exception
  {
    String testName = "attributesVisibleOutsideRootDSE";
    debugInfo(testName, "Starting test \n\n");

    Set<String> attributes =
        newSet("firstchangenumber", "lastchangenumber", "changelog", "lastExternalChangelogCookie");

    InternalSearchOperation searchOp = searchDNWithBaseScope(TEST_ROOT_DN_STRING, attributes);
    waitForSearchOpResult(searchOp, ResultCode.SUCCESS);

    final List<SearchResultEntry> entries = searchOp.getSearchEntries();
    assertThat(entries).hasSize(1);
    debugAndWriteEntries(null, entries, testName);
    SearchResultEntry entry = entries.get(0);
    assertNull(getAttributeValue(entry, "firstchangenumber"));
    assertNull(getAttributeValue(entry, "lastchangenumber"));
    assertNull(getAttributeValue(entry, "changelog"));
    assertNull(getAttributeValue(entry, "lastExternalChangelogCookie"));

    debugInfo(testName, "Ending test with success");
  }

  @DataProvider()
  public Object[][] getFilters()
  {
    return new Object[][] {
      // base DN, filter, expected first change number, expected last change number
      { "cn=changelog", "(objectclass=*)", -1, -1 },
      { "cn=changelog", "(changenumber>=2)", 2, -1 },
      { "cn=changelog", "(&(changenumber>=2)(changenumber<=5))", 2, 5 },
      { "cn=changelog", "(&(dc=x)(&(changenumber>=2)(changenumber<=5)))", 2, 5 },
      { "cn=changelog",
          "(&(&(changenumber>=3)(changenumber<=4))(&(|(dc=y)(dc=x))(&(changenumber>=2)(changenumber<=5))))", 3, 4 },
      { "cn=changelog", "(|(objectclass=*)(&(changenumber>=2)(changenumber<=5)))", -1, -1 },
      { "cn=changelog", "(changenumber=8)", 8, 8 },

      { "changeNumber=8,cn=changelog", "(objectclass=*)", 8, 8 },
      { "changeNumber=8,cn=changelog", "(changenumber>=2)", 8, 8 },
      { "changeNumber=8,cn=changelog", "(&(changenumber>=2)(changenumber<=5))", 8, 8 },
    };
  }

  @Test(dataProvider="getFilters")
  public void optimizeFiltersWithChangeNumber(String dn, String filter, long expectedFirstCN, long expectedLastCN)
      throws Exception
  {
    final ChangelogBackend backend = new ChangelogBackend(null, null);
    final DN baseDN = DN.decode(dn);
    final SearchParams searchParams = new SearchParams();

    backend.optimizeSearchParameters(searchParams, baseDN, SearchFilter.createFilterFromString(filter));

    assertSearchParameters(searchParams, expectedFirstCN, expectedLastCN, null);
  }

  @Test
  public void optimizeFiltersWithReplicationCsn() throws Exception
  {
    final ChangelogBackend backend = new ChangelogBackend(null, null);
    final DN baseDN = DN.decode("cn=changelog");
    final CSN csn = new CSNGenerator(1, 0).newCSN();
    final SearchParams searchParams = new SearchParams();

    backend.optimizeSearchParameters(searchParams, baseDN,
        SearchFilter.createFilterFromString("(replicationcsn=" + csn + ")"));

    assertSearchParameters(searchParams, -1, -1, csn);
  }

  private List<SearchResultEntry> assertChangelogAttributesInRootDSE(boolean isECLEnabled,
      int expectedFirstChangeNumber, int expectedLastChangeNumber) throws Exception
  {
    AssertionError error = null;
    for (int count = 0 ; count < 30; count++)
    {
      try
      {
        final Set<String> attributes = new LinkedHashSet<String>();
        if (expectedFirstChangeNumber > 0)
        {
          attributes.add("firstchangenumber");
        }
        attributes.add("lastchangenumber");
        attributes.add("changelog");
        attributes.add("lastExternalChangelogCookie");

        final InternalSearchOperation searchOp = searchDNWithBaseScope("", attributes);
        final List<SearchResultEntry> entries = searchOp.getSearchEntries();
        assertThat(entries).hasSize(1);

        final SearchResultEntry entry = entries.get(0);
        if (isECLEnabled)
        {
          if (expectedFirstChangeNumber > 0)
          {
            assertAttributeValue(entry, "firstchangenumber", String.valueOf(expectedFirstChangeNumber));
          }
          assertAttributeValue(entry, "lastchangenumber", String.valueOf(expectedLastChangeNumber));
          assertAttributeValue(entry, "changelog", String.valueOf("cn=changelog"));
          assertNotNull(getAttributeValue(entry, "lastExternalChangelogCookie"));
        }
        else
        {
          if (expectedFirstChangeNumber > 0) {
            assertNull(getAttributeValue(entry, "firstchangenumber"));
          }
          assertNull(getAttributeValue(entry, "lastchangenumber"));
          assertNull(getAttributeValue(entry, "changelog"));
          assertNull(getAttributeValue(entry, "lastExternalChangelogCookie"));
        }
        return entries;
      }
      catch (AssertionError ae)
      {
        // try again to see if changes have been persisted
        error = ae;
      }
      Thread.sleep(100);
    }
    assertNotNull(error);
    throw error;
  }

  private void assertSearchParameters(SearchParams searchParams, long firstChangeNumber,
      long lastChangeNumber, CSN csn) throws Exception
  {
    assertEquals(searchParams.getLowestChangeNumber(), firstChangeNumber);
    assertEquals(searchParams.getHighestChangeNumber(), lastChangeNumber);
    assertEquals(searchParams.getCSN(), csn == null ? new CSN(0, 0, 0) : csn);
  }

  private CSN[] generateAndPublishChangesForEachOperationType(String testName) throws Exception
  {
    CSN[] csns = generateCSNs(4, SERVER_ID_1);

    List<UpdateMsg> messages = new ArrayList<UpdateMsg>();
    messages.add(generateDeleteMsg(TEST_ROOT_DN_STRING, csns[0], testName, 1));
    messages.add(generateAddMsg(TEST_ROOT_DN_STRING, csns[1], USER1_ENTRY_UUID, testName));
    messages.add(generateModMsg(TEST_ROOT_DN_STRING, csns[2], testName));
    messages.add(generateModDNMsg(TEST_ROOT_DN_STRING, csns[3], testName));

    publishChanges(testName, messages.toArray(new UpdateMsg[4]));
    return csns;
  }

  /** Publish a list changes to the default replication broker used by tests. */
  private void publishChanges(String testName, UpdateMsg...messages) throws Exception
  {
    Pair<ReplicationBroker, LDAPReplicationDomain> replicationObjects = null;
    try
    {
      replicationObjects = enableReplication(ROOT_DN_OTEST, SERVER_ID_1, replicationServerPort, brokerSessionTimeout);
      ReplicationBroker broker = replicationObjects.getFirst();
      for (UpdateMsg msg : messages)
      {
        debugInfo(testName, " publishes " + msg.getCSN());
        broker.publish(msg);
      }
    }
    finally
    {
      if (replicationObjects != null)
      {
        removeReplicationDomains(replicationObjects.getSecond());
        stop(replicationObjects.getFirst());
      }
    }
  }

  private void searchChangesForEachOperationTypeUsingEmptyCookie(CSN[] csns, String testName) throws Exception
  {
    int nbEntries = 4;
    String cookie= "";

    InternalSearchOperation searchOp =
        searchChangelogUsingCookie("(targetdn=*" + testName + "*,o=test)", cookie, nbEntries, SUCCESS, testName);

    final String[] cookies = new String[nbEntries];
    for (int j = 0; j < cookies.length; j++)
    {
      cookies[j] = "o=test:" + csns[j] + ";";
    }
    final List<SearchResultEntry> searchEntries = searchOp.getSearchEntries();
    assertDelEntry(searchEntries.get(0), testName + 1, testName + "uuid1", CHANGENUMBER_ZERO, csns[0], cookies[0]);
    assertAddEntry(searchEntries.get(1), testName + 2, USER1_ENTRY_UUID, CHANGENUMBER_ZERO, csns[1], cookies[1]);
    assertModEntry(searchEntries.get(2), testName + 3, testName + "uuid3", CHANGENUMBER_ZERO, csns[2], cookies[2]);
    assertModDNEntry(searchEntries.get(3), testName + 4, testName + "new4", testName+"uuid4", CHANGENUMBER_ZERO,
        csns[3], cookies[3]);
    assertResultsContainCookieControl(searchOp, cookies);
  }

  private void searchChangesForEachOperationTypeUsingDraftMode(long firstChangeNumber, CSN[] csns, String testName)
      throws Exception
  {
    // Search the changelog and check 4 entries are returned
    String filter = "(targetdn=*" + testName + "*,o=test)";
    InternalSearchOperation searchOp = searchChangelog(filter, 4, SUCCESS, testName);

    assertContainsNoControl(searchOp);
    assertEntriesForEachOperationType(searchOp.getSearchEntries(), firstChangeNumber, testName, USER1_ENTRY_UUID, csns);

    // Search the changelog with filter on change number and check 4 entries are returned
    filter =
        "(&(targetdn=*" + testName + "*,o=test)"
          + "(&(changenumber>=" + firstChangeNumber + ")"
            + "(changenumber<=" + (firstChangeNumber + 3) + ")))";
    searchOp = searchChangelog(filter, 4, SUCCESS, testName);

    assertContainsNoControl(searchOp);
    assertEntriesForEachOperationType(searchOp.getSearchEntries(), firstChangeNumber, testName, USER1_ENTRY_UUID, csns);
  }

  /**
   * Search on the provided change number and check the result.
   *
   * @param changeNumber
   *          Change number to search
   * @param expectedCsn
   *          Expected CSN in the entry corresponding to the change number
   */
  private void searchChangelogForOneChangeNumber(long changeNumber, CSN expectedCsn) throws Exception
  {
    String testName = "searchOneChangeNumber/" + changeNumber;
    debugInfo(testName, "Starting search\n\n");

    InternalSearchOperation searchOp =
        searchChangelog("(changenumber=" + changeNumber + ")", 1, SUCCESS, testName);

    SearchResultEntry entry = searchOp.getSearchEntries().get(0);
    String uncheckedUid = null;
    assertEntryCommonAttributes(entry, uncheckedUid, USER1_ENTRY_UUID, changeNumber, expectedCsn,
        "o=test:" + expectedCsn + ";");

    debugInfo(testName, "Ending search with success");
  }

  private void searchChangelogFromToChangeNumber(int firstChangeNumber, int lastChangeNumber) throws Exception
  {
    String testName = "searchFromToChangeNumber/" + firstChangeNumber + "/" + lastChangeNumber;
    debugInfo(testName, "Starting search\n\n");

    String filter = "(&(changenumber>=" + firstChangeNumber + ")" + "(changenumber<=" + lastChangeNumber + "))";
    final int expectedNbEntries = lastChangeNumber - firstChangeNumber + 1;
    searchChangelog(filter, expectedNbEntries, SUCCESS, testName);

    debugInfo(testName, "Ending search with success");
  }

  private InternalSearchOperation searchChangelogUsingCookie(String filterString,
      String cookie, int expectedNbEntries, ResultCode expectedResultCode, String testName)
      throws Exception
  {
    debugInfo(testName, "Search with cookie=[" + cookie + "] filter=[" + filterString + "]");
    return searchChangelog(filterString, ALL_ATTRIBUTES, createCookieControl(cookie),
        expectedNbEntries, expectedResultCode, testName);
  }

  private InternalSearchOperation searchChangelog(String filterString, int expectedNbEntries,
      ResultCode expectedResultCode, String testName) throws Exception
  {
    return searchChangelog(filterString, ALL_ATTRIBUTES, NO_CONTROL, expectedNbEntries, expectedResultCode, testName);
  }

  private InternalSearchOperation searchChangelog(String filterString, Set<String> attributes,
      List<Control> controls, int expectedNbEntries, ResultCode expectedResultCode, String testName) throws Exception
  {
    InternalSearchOperation searchOperation = null;
    int sizeLimitZero = 0;
    int timeLimitZero = 0;
    InternalSearchListener noSearchListener = null;
    int count = 0;
    do
    {
      Thread.sleep(10);
      boolean typesOnlyFalse = false;
      searchOperation = connection.processSearch("cn=changelog", SearchScope.WHOLE_SUBTREE,
          DereferencePolicy.NEVER_DEREF_ALIASES, sizeLimitZero, timeLimitZero, typesOnlyFalse, filterString,
          attributes, controls, noSearchListener);
      count++;
    }
    while (count < 300 && searchOperation.getSearchEntries().size() != expectedNbEntries);

    final List<SearchResultEntry> entries = searchOperation.getSearchEntries();
    assertThat(entries).hasSize(expectedNbEntries);
    debugAndWriteEntries(getLDIFWriter(), entries, testName);
    waitForSearchOpResult(searchOperation, expectedResultCode);
    return searchOperation;
  }

  private InternalSearchOperation searchDNWithBaseScope(String dn, Set<String> attributes) throws Exception
  {
    final InternalSearchOperation searchOp = connection.processSearch(
        dn,
        SearchScope.BASE_OBJECT,
        DereferencePolicy.NEVER_DEREF_ALIASES,
        0,     // Size limit
        0,     // Time limit
        false, // Types only
        "(objectclass=*)",
        attributes);
    waitForSearchOpResult(searchOp, ResultCode.SUCCESS);
    return searchOp;
  }

  /** Build a list of controls including the cookie provided. */
  private List<Control> createCookieControl(String cookie) throws DirectoryException
  {
    final MultiDomainServerState state = new MultiDomainServerState(cookie);
    final Control cookieControl = new ExternalChangelogRequestControl(true, state);
    return newList(cookieControl);
  }

  private static LDIFWriter getLDIFWriter() throws Exception
  {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    LDIFExportConfig exportConfig = new LDIFExportConfig(stream);
    return new LDIFWriter(exportConfig);
  }

  private CSN[] generateCSNs(int numberOfCsns, int serverId)
  {
    long startTime = TimeThread.getTime();

    CSN[] csns = new CSN[numberOfCsns];
    for (int i = 0; i < numberOfCsns; i++)
    {
      // seqNum must be greater than 0, so start at 1
      csns[i] = new CSN(startTime + i, i + 1, serverId);
    }
    return csns;
  }

  private UpdateMsg generateDeleteMsg(String baseDn, CSN csn, String testName, int testIndex)
      throws Exception
  {
    String dn = "uid=" + testName + testIndex + "," + baseDn;
    return new DeleteMsg(DN.decode(dn), csn, testName + "uuid" + testIndex);
  }

  private UpdateMsg generateAddMsg(String baseDn, CSN csn, String user1entryUUID, String testName)
      throws Exception
  {
    String baseUUID = "22222222-2222-2222-2222-222222222222";
    String entryLdif = "dn: uid="+ testName + "2," + baseDn + "\n"
        + "objectClass: top\n" + "objectClass: domain\n"
        + "entryUUID: "+ user1entryUUID +"\n";
    Entry entry = TestCaseUtils.entryFromLdifString(entryLdif);
    return new AddMsg(
        csn,
        DN.decode("uid="+testName+"2," + baseDn),
        user1entryUUID,
        baseUUID,
        entry.getObjectClassAttribute(),
        entry.getAttributes(),
        Collections.<Attribute> emptyList());
  }

  private UpdateMsg generateModMsg(String baseDn, CSN csn, String testName) throws Exception
  {
    DN baseDN = DN.decode("uid=" + testName + "3," + baseDn);
    List<Modification> mods = createAttributeModif("description", "new value");
    return new ModifyMsg(csn, baseDN, mods, testName + "uuid3");
  }

  private List<Modification> createAttributeModif(String attributeName, String valueString)
  {
    Attribute attr = Attributes.create(attributeName, valueString);
    return newList(new Modification(ModificationType.REPLACE, attr));
  }

  private UpdateMsg generateModDNMsg(String baseDn, CSN csn, String testName) throws Exception
  {
    final DN newSuperior = ROOT_DN_OTEST2;
    ModifyDNOperation op = new ModifyDNOperationBasis(connection, 1, 1, null,
        DN.decode("uid=" + testName + "4," + baseDn), // entryDN
        RDN.decode("uid=" + testName + "new4"), // new rdn
        true,  // deleteoldrdn
        newSuperior);
    op.setAttachment(SYNCHROCONTEXT, new ModifyDnContext(csn, testName + "uuid4", "newparentId"));
    LocalBackendModifyDNOperation localOp = new LocalBackendModifyDNOperation(op);
    return new ModifyDNMsg(localOp);
  }

  //TODO : share this code with other classes ?
  private void waitForSearchOpResult(Operation operation, ResultCode expectedResult) throws Exception
  {
    int i = 0;
    while (operation.getResultCode() == ResultCode.UNDEFINED || operation.getResultCode() != expectedResult)
    {
      Thread.sleep(50);
      i++;
      if (i > 10)
      {
        assertEquals(operation.getResultCode(), expectedResult, operation.getErrorMessage().toString());
      }
    }
  }

  /** Verify that no entry contains the ChangeLogCookie control. */
  private void assertContainsNoControl(InternalSearchOperation searchOp)
  {
    for (SearchResultEntry entry : searchOp.getSearchEntries())
    {
      assertTrue(entry.getControls().isEmpty(), "result entry " + entry.toString() +
          " should contain no control(s)");
    }
  }

  /** Verify that all entries contains the ChangeLogCookie control with the correct cookie value. */
  private void assertResultsContainCookieControl(InternalSearchOperation searchOp, String[] cookies) throws Exception
  {
    for (SearchResultEntry entry : searchOp.getSearchEntries())
    {
      boolean cookieControlFound = false;
      for (Control control : entry.getControls())
      {
        if (control.getOID().equals(OID_ECL_COOKIE_EXCHANGE_CONTROL))
        {
          String cookieString =
              searchOp.getRequestControl(ExternalChangelogRequestControl.DECODER).getCookie().toString();
          assertThat(cookieString).isIn((Object[]) cookies);
          cookieControlFound = true;
        }
      }
      assertTrue(cookieControlFound, "result entry " + entry.toString() + " should contain the cookie control");
    }
  }

  /** Check the DEL entry has the right content. */
  private void assertDelEntry(SearchResultEntry entry, String uid, String entryUUID,
      long changeNumber, CSN csn, String cookie)
  {
    assertAttributeValue(entry, "changetype", "delete");
    assertAttributeValue(entry, "targetuniqueid", entryUUID);
    assertAttributeValue(entry, "targetentryuuid", entryUUID);
    assertEntryCommonAttributes(entry, uid, entryUUID, changeNumber, csn, cookie);
  }

  /** Check the ADD entry has the right content. */
  private void assertAddEntry(SearchResultEntry entry, String uid, String entryUUID,
      long changeNumber, CSN csn, String cookie)
  {
    assertAttributeValue(entry, "changetype", "add");
    assertEntryMatchesLDIF(entry, "changes",
        "objectClass: domain",
        "objectClass: top",
        "entryUUID: " + entryUUID);
    assertEntryCommonAttributes(entry, uid, entryUUID, changeNumber, csn, cookie);
  }

  private void assertModEntry(SearchResultEntry entry, String uid, String entryUUID,
      long changeNumber, CSN csn, String cookie)
  {
    assertAttributeValue(entry, "changetype", "modify");
    assertEntryMatchesLDIF(entry, "changes",
        "replace: description",
        "description: new value",
        "-");
    assertEntryCommonAttributes(entry, uid, entryUUID, changeNumber, csn, cookie);
  }

  private void assertModDNEntry(SearchResultEntry entry, String uid, String newUid,
      String entryUUID, long changeNumber, CSN csn, String cookie)
  {
    assertAttributeValue(entry, "changetype", "modrdn");
    assertAttributeValue(entry, "newrdn", "uid=" + newUid);
    assertAttributeValue(entry, "newsuperior", TEST_ROOT_DN_STRING2);
    assertAttributeValue(entry, "deleteoldrdn", "true");
    assertEntryCommonAttributes(entry, uid, entryUUID, changeNumber, csn, cookie);

  }

  private void assertEntryCommonAttributes(SearchResultEntry resultEntry,
      String uid, String entryUUID, long changeNumber, CSN csn, String cookie)
  {
    if (changeNumber == 0)
    {
      assertDNWithCSN(resultEntry, csn);
    }
    else
    {
      assertDNWithChangeNumber(resultEntry, changeNumber);
      assertAttributeValue(resultEntry, "changenumber", String.valueOf(changeNumber));
    }
    assertAttributeValue(resultEntry, "targetentryuuid", entryUUID);
    assertAttributeValue(resultEntry, "replicaidentifier", String.valueOf(SERVER_ID_1));
    assertAttributeValue(resultEntry, "replicationcsn", csn.toString());
    assertAttributeValue(resultEntry, "changelogcookie", cookie);
    // A null value can be provided for uid if it should not be checked
    if (uid != null)
    {
      final String targetDN = "uid=" + uid + "," + TEST_ROOT_DN_STRING;
      assertAttributeValue(resultEntry, "targetdn", targetDN);
    }
  }

  private void assertEntriesForEachOperationType(List<SearchResultEntry> entries, long firstChangeNumber,
      String testName, String entryUUID, CSN... csns) throws Exception
  {
    debugAndWriteEntries(getLDIFWriter(), entries, testName);

    assertThat(entries).hasSize(4);

    CSN csn = csns[0];
    assertDelEntry(entries.get(0), testName + "1", testName + "uuid1", firstChangeNumber, csn, "o=test:" + csn + ";");

    csn = csns[1];
    assertAddEntry(entries.get(1), testName + "2", entryUUID, firstChangeNumber+1, csn, "o=test:" + csn + ";");

    csn = csns[2];
    assertModEntry(entries.get(2), testName + "3", testName + "uuid3", firstChangeNumber+2, csn,
        "o=test:" + csn + ";");

    csn = csns[3];
    assertModDNEntry(entries.get(3), testName + "4", testName + "new4", testName + "uuid4", firstChangeNumber+3, csn,
        "o=test:" + csn + ";");
  }

  /**
   * Asserts the attribute value as LDIF to ignore lines ordering.
   */
  private static void assertEntryMatchesLDIF(Entry entry, String attrName, String... expectedLDIFLines)
  {
    final String actualVal = getAttributeValue(entry, attrName);
    final Set<Set<String>> actual = toLDIFEntries(actualVal.split("\n"));
    final Set<Set<String>> expected = toLDIFEntries(expectedLDIFLines);
    assertThat(actual)
        .as("In entry " + entry + " incorrect value for attr '" + attrName + "'")
        .isEqualTo(expected);
  }

  private static void assertAttributeValue(Entry entry, String attrName, String expectedValue)
  {
    assertFalse(expectedValue.contains("\n"),
        "You should use assertEntryMatchesLDIF() method for asserting on this value: \"" + expectedValue + "\"");
    final String actualValue = getAttributeValue(entry, attrName);
    assertThat(actualValue)
        .as("In entry " + entry + " incorrect value for attr '" + attrName + "'")
        .isEqualToIgnoringCase(expectedValue);
  }

  private void assertDNWithChangeNumber(SearchResultEntry resultEntry, long changeNumber)
  {
    String actualDN = resultEntry.getDN().toNormalizedString();
    String expectedDN = "changenumber=" + changeNumber + ",cn=changelog";
    assertThat(actualDN).isEqualToIgnoringCase(expectedDN);
  }

  private void assertDNWithCSN(SearchResultEntry resultEntry, CSN csn)
  {
    String actualDN = resultEntry.getDN().toNormalizedString();
    String expectedDN = "replicationcsn=" + csn + "," + TEST_ROOT_DN_STRING + ",cn=changelog";
    assertThat(actualDN).isEqualToIgnoringCase(expectedDN);
  }

  /**
   * Returns a data structure allowing to compare arbitrary LDIF lines. The
   * algorithm splits LDIF entries on lines containing only a dash ("-"). It
   * then returns LDIF entries and lines in an LDIF entry in ordering
   * insensitive data structures.
   * <p>
   * Note: a last line with only a dash ("-") is significant. i.e.:
   *
   * <pre>
   * <code>
   * boolean b = toLDIFEntries("-").equals(toLDIFEntries()));
   * System.out.println(b); // prints "false"
   * </code>
   * </pre>
   */
  private static Set<Set<String>> toLDIFEntries(String... ldifLines)
  {
    final Set<Set<String>> results = new HashSet<Set<String>>();
    Set<String> ldifEntryLines = new HashSet<String>();
    for (String ldifLine : ldifLines)
    {
      if (!"-".equals(ldifLine))
      {
        // same entry keep adding
        ldifEntryLines.add(ldifLine);
      }
      else
      {
        // this is a new entry
        results.add(ldifEntryLines);
        ldifEntryLines = new HashSet<String>();
      }
    }
    results.add(ldifEntryLines);
    return results;
  }

  // TODO : share this code with other classes
  private static String getAttributeValue(Entry entry, String attrName)
  {
    List<Attribute> attrs = entry.getAttribute(attrName.toLowerCase());
    if (attrs == null)
    {
      return null;
    }
    Attribute a = attrs.iterator().next();
    AttributeValue av = a.iterator().next();
    return av.toString();
  }

  private void debugAndWriteEntries(LDIFWriter ldifWriter,List<SearchResultEntry> entries, String tn) throws Exception
  {
    if (entries != null)
    {
      for (SearchResultEntry entry : entries)
      {
        // Can use entry.toSingleLineString()
        debugInfo(tn, " RESULT entry returned:" + entry.toLDIFString());
        if (ldifWriter != null)
        {
          ldifWriter.writeEntry(entry);
        }
      }
    }
  }

  /**
   * Utility - log debug message - highlight it is from the test and not
   * from the server code. Makes easier to observe the test steps.
   */
  private void debugInfo(String testName, String message)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("** TEST " + testName + " ** " + message);
    }
  }
}
