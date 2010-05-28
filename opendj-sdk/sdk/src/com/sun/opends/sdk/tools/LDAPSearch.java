/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package com.sun.opends.sdk.tools;



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.tools.ToolConstants.*;
import static com.sun.opends.sdk.tools.Utils.filterExitCode;

import java.io.*;
import java.util.*;

import org.opends.sdk.*;
import org.opends.sdk.controls.*;
import org.opends.sdk.ldif.EntryWriter;
import org.opends.sdk.ldif.LDIFEntryWriter;
import org.opends.sdk.requests.Requests;
import org.opends.sdk.requests.SearchRequest;
import org.opends.sdk.responses.Responses;
import org.opends.sdk.responses.Result;
import org.opends.sdk.responses.SearchResultEntry;
import org.opends.sdk.responses.SearchResultReference;

import com.sun.opends.sdk.controls.AccountUsabilityResponseControl;
import com.sun.opends.sdk.util.StaticUtils;



/**
 * A tool that can be used to issue Search requests to the Directory Server.
 */
public final class LDAPSearch extends ConsoleApplication
{
  private class LDAPSearchResultHandler implements SearchResultHandler
  {
    private int entryCount = 0;



    /**
     * {@inheritDoc}
     */
    public boolean handleEntry(final SearchResultEntry entry)
    {
      entryCount++;

      try
      {
        final EntryChangeNotificationResponseControl control = entry
            .getControl(EntryChangeNotificationResponseControl.DECODER,
                new DecodeOptions());
        if (control != null)
        {
          println(INFO_LDAPSEARCH_PSEARCH_CHANGE_TYPE.get(control
              .getChangeType().toString()));
          final DN previousDN = control.getPreviousName();
          if (previousDN != null)
          {
            println(INFO_LDAPSEARCH_PSEARCH_PREVIOUS_DN.get(previousDN
                .toString()));
          }
        }
      }
      catch (final DecodeException de)
      {
        println(ERR_DECODE_CONTROL_FAILURE.get(de.getLocalizedMessage()));
      }

      try
      {
        final AccountUsabilityResponseControl control = entry.getControl(
            AccountUsabilityResponseControl.DECODER, new DecodeOptions());

        if (control != null)
        {
          println(INFO_LDAPSEARCH_ACCTUSABLE_HEADER.get());
          if (control.isUsable())
          {

            println(INFO_LDAPSEARCH_ACCTUSABLE_IS_USABLE.get());
            if (control.getSecondsBeforeExpiration() > 0)
            {
              final int timeToExp = control.getSecondsBeforeExpiration();
              final LocalizableMessage timeToExpStr = Utils
                  .secondsToTimeString(timeToExp);

              println(INFO_LDAPSEARCH_ACCTUSABLE_TIME_UNTIL_EXPIRATION
                  .get(timeToExpStr));
            }
          }
          else
          {

            println(INFO_LDAPSEARCH_ACCTUSABLE_NOT_USABLE.get());
            if (control.isInactive())
            {
              println(INFO_LDAPSEARCH_ACCTUSABLE_ACCT_INACTIVE.get());
            }
            if (control.isReset())
            {
              println(INFO_LDAPSEARCH_ACCTUSABLE_PW_RESET.get());
            }
            if (control.isExpired())
            {
              println(INFO_LDAPSEARCH_ACCTUSABLE_PW_EXPIRED.get());

              if (control.getRemainingGraceLogins() > 0)
              {
                println(INFO_LDAPSEARCH_ACCTUSABLE_REMAINING_GRACE.get(control
                    .getRemainingGraceLogins()));
              }
            }
            if (control.isLocked())
            {
              println(INFO_LDAPSEARCH_ACCTUSABLE_LOCKED.get());
              if (control.getSecondsBeforeUnlock() > 0)
              {
                final int timeToUnlock = control.getSecondsBeforeUnlock();
                final LocalizableMessage timeToUnlockStr = Utils
                    .secondsToTimeString(timeToUnlock);

                println(INFO_LDAPSEARCH_ACCTUSABLE_TIME_UNTIL_UNLOCK
                    .get(timeToUnlockStr));
              }
            }
          }
        }
      }
      catch (final DecodeException de)
      {
        println(ERR_DECODE_CONTROL_FAILURE.get(de.getLocalizedMessage()));
      }

      try
      {
        ldifWriter.writeEntry(entry);
        ldifWriter.flush();
      }
      catch (final IOException ioe)
      {
        // Something is seriously wrong
        throw new RuntimeException(ioe);
      }
      return true;
    }



    /**
     * {@inheritDoc}
     */
    public boolean handleReference(final SearchResultReference reference)
    {
      println(LocalizableMessage.raw(reference.toString()));
      return true;
    }
  }



  /**
   * The main method for LDAPSearch tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   */

  public static void main(final String[] args)
  {
    final int retCode = mainSearch(args, false, System.in, System.out,
        System.err);
    System.exit(filterExitCode(retCode));
  }



  /**
   * Parses the provided command-line arguments and uses that information to run
   * the ldapsearch tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   * @return The error code.
   */

  static int mainSearch(final String[] args)
  {
    return mainSearch(args, true, System.in, System.out, System.err);
  }



  /**
   * Parses the provided command-line arguments and uses that information to run
   * the ldapsearch tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   * @param returnMatchingEntries
   *          whether when the option --countEntries is specified, the number of
   *          matching entries should be returned or not.
   * @param inStream
   *          The input stream to use for standard input, or <CODE>null</CODE>
   *          if standard input is not needed.
   * @param outStream
   *          The output stream to use for standard output, or <CODE>null</CODE>
   *          if standard output is not needed.
   * @param errStream
   *          The output stream to use for standard error, or <CODE>null</CODE>
   *          if standard error is not needed.
   * @return The error code.
   */

  static int mainSearch(final String[] args,
      final boolean returnMatchingEntries, final InputStream inStream,
      final OutputStream outStream, final OutputStream errStream)
  {
    return new LDAPSearch(inStream, outStream, errStream).run(args,
        returnMatchingEntries);
  }



  /**
   * Parses the provided command-line arguments and uses that information to run
   * the ldapsearch tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   * @param inStream
   *          The input stream to use for standard input, or <CODE>null</CODE>
   *          if standard input is not needed.
   * @param outStream
   *          The output stream to use for standard output, or <CODE>null</CODE>
   *          if standard output is not needed.
   * @param errStream
   *          The output stream to use for standard error, or <CODE>null</CODE>
   *          if standard error is not needed.
   * @return The error code.
   */
  static int mainSearch(final String[] args, final InputStream inStream,
      final OutputStream outStream, final OutputStream errStream)
  {
    return mainSearch(args, true, inStream, outStream, errStream);
  }



  private BooleanArgument verbose;

  private EntryWriter ldifWriter;



  private LDAPSearch(final InputStream in, final OutputStream out,
      final OutputStream err)
  {
    super(in, out, err);

  }



  /**
   * Indicates whether or not the user has requested advanced mode.
   *
   * @return Returns <code>true</code> if the user has requested advanced mode.
   */
  @Override
  public boolean isAdvancedMode()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested interactive behavior.
   *
   * @return Returns <code>true</code> if the user has requested interactive
   *         behavior.
   */
  @Override
  public boolean isInteractive()
  {
    return true;
  }



  /**
   * Indicates whether or not this console application is running in its
   * menu-driven mode. This can be used to dictate whether output should go to
   * the error stream or not. In addition, it may also dictate whether or not
   * sub-menus should display a cancel option as well as a quit option.
   *
   * @return Returns <code>true</code> if this console application is running in
   *         its menu-driven mode.
   */
  @Override
  public boolean isMenuDrivenMode()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested quiet output.
   *
   * @return Returns <code>true</code> if the user has requested quiet output.
   */
  @Override
  public boolean isQuiet()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested script-friendly output.
   *
   * @return Returns <code>true</code> if the user has requested script-friendly
   *         output.
   */
  @Override
  public boolean isScriptFriendly()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested verbose output.
   *
   * @return Returns <code>true</code> if the user has requested verbose output.
   */
  @Override
  public boolean isVerbose()
  {
    return verbose.isPresent();
  }



  private int run(final String[] args, final boolean returnMatchingEntries)
  {
    // Create the command-line argument parser for use with this
    // program.
    final LocalizableMessage toolDescription = INFO_LDAPSEARCH_TOOL_DESCRIPTION
        .get();
    final ArgumentParser argParser = new ArgumentParser(LDAPSearch.class
        .getName(), toolDescription, false, true, 0, 0,
        "[filter] [attributes ...]");
    ArgumentParserConnectionFactory connectionFactory;

    BooleanArgument countEntries;
    BooleanArgument dontWrap;
    BooleanArgument noop;
    BooleanArgument typesOnly;
    IntegerArgument simplePageSize;
    IntegerArgument timeLimit;
    IntegerArgument version;
    StringArgument baseDN;
    StringArgument controlStr;
    MultiChoiceArgument<DereferenceAliasesPolicy> dereferencePolicy;
    StringArgument filename;
    StringArgument matchedValuesFilter;
    StringArgument pSearchInfo;
    MultiChoiceArgument<SearchScope> searchScope;
    StringArgument vlvDescriptor;
    StringArgument effectiveRightsUser;
    StringArgument effectiveRightsAttrs;
    StringArgument sortOrder;
    StringArgument proxyAuthzID;
    StringArgument assertionFilter;
    IntegerArgument sizeLimit;
    try
    {
      connectionFactory = new ArgumentParserConnectionFactory(argParser, this);
      final StringArgument propertiesFileArgument = new StringArgument(
          "propertiesFilePath", null, OPTION_LONG_PROP_FILE_PATH, false, false,
          true, INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_PROP_FILE_PATH.get());
      argParser.addArgument(propertiesFileArgument);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

      final BooleanArgument noPropertiesFileArgument = new BooleanArgument(
          "noPropertiesFileArgument", null, OPTION_LONG_NO_PROP_FILE,
          INFO_DESCRIPTION_NO_PROP_FILE.get());
      argParser.addArgument(noPropertiesFileArgument);
      argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      baseDN = new StringArgument("baseDN", OPTION_SHORT_BASEDN,
          OPTION_LONG_BASEDN, true, false, true, INFO_BASEDN_PLACEHOLDER.get(),
          null, null, INFO_SEARCH_DESCRIPTION_BASEDN.get());
      baseDN.setPropertyName(OPTION_LONG_BASEDN);
      argParser.addArgument(baseDN);

      searchScope = new MultiChoiceArgument<SearchScope>("searchScope", 's',
          "searchScope", false, true, INFO_SEARCH_SCOPE_PLACEHOLDER.get(),
          SearchScope.values(), false, INFO_SEARCH_DESCRIPTION_SEARCH_SCOPE
              .get());
      searchScope.setPropertyName("searchScope");
      searchScope.setDefaultValue(SearchScope.WHOLE_SUBTREE);
      argParser.addArgument(searchScope);

      filename = new StringArgument("filename", OPTION_SHORT_FILENAME,
          OPTION_LONG_FILENAME, false, false, true,
          INFO_FILE_PLACEHOLDER.get(), null, null,
          INFO_SEARCH_DESCRIPTION_FILENAME.get());
      searchScope.setPropertyName(OPTION_LONG_FILENAME);
      argParser.addArgument(filename);

      proxyAuthzID = new StringArgument("proxy_authzid",
          OPTION_SHORT_PROXYAUTHID, OPTION_LONG_PROXYAUTHID, false, false,
          true, INFO_PROXYAUTHID_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_PROXY_AUTHZID.get());
      proxyAuthzID.setPropertyName(OPTION_LONG_PROXYAUTHID);
      argParser.addArgument(proxyAuthzID);

      pSearchInfo = new StringArgument("psearchinfo", 'C', "persistentSearch",
          false, false, true, INFO_PSEARCH_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_PSEARCH_INFO.get());
      pSearchInfo.setPropertyName("persistentSearch");
      argParser.addArgument(pSearchInfo);

      simplePageSize = new IntegerArgument("simplepagesize", null,
          "simplePageSize", false, false, true, INFO_NUM_ENTRIES_PLACEHOLDER
              .get(), 1000, null, true, 1, false, 0,
          INFO_DESCRIPTION_SIMPLE_PAGE_SIZE.get());
      simplePageSize.setPropertyName("simplePageSize");
      argParser.addArgument(simplePageSize);

      assertionFilter = new StringArgument("assertionfilter", null,
          OPTION_LONG_ASSERTION_FILE, false, false, true,
          INFO_ASSERTION_FILTER_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_ASSERTION_FILTER.get());
      assertionFilter.setPropertyName(OPTION_LONG_ASSERTION_FILE);
      argParser.addArgument(assertionFilter);

      matchedValuesFilter = new StringArgument("matchedvalues", null,
          "matchedValuesFilter", false, true, true, INFO_FILTER_PLACEHOLDER
              .get(), null, null, INFO_DESCRIPTION_MATCHED_VALUES_FILTER.get());
      matchedValuesFilter.setPropertyName("matchedValuesFilter");
      argParser.addArgument(matchedValuesFilter);

      sortOrder = new StringArgument("sortorder", 'S', "sortOrder", false,
          false, true, INFO_SORT_ORDER_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_SORT_ORDER.get());
      sortOrder.setPropertyName("sortOrder");
      argParser.addArgument(sortOrder);

      vlvDescriptor = new StringArgument("vlvdescriptor", 'G',
          "virtualListView", false, false, true, INFO_VLV_PLACEHOLDER.get(),
          null, null, INFO_DESCRIPTION_VLV.get());
      vlvDescriptor.setPropertyName("virtualListView");
      argParser.addArgument(vlvDescriptor);

      controlStr = new StringArgument("control", 'J', "control", false, true,
          true, INFO_LDAP_CONTROL_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_CONTROLS.get());
      controlStr.setPropertyName("control");
      argParser.addArgument(controlStr);

      effectiveRightsUser = new StringArgument("effectiveRightsUser",
          OPTION_SHORT_EFFECTIVERIGHTSUSER, OPTION_LONG_EFFECTIVERIGHTSUSER,
          false, false, true, INFO_PROXYAUTHID_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_EFFECTIVERIGHTS_USER.get());
      effectiveRightsUser.setPropertyName(OPTION_LONG_EFFECTIVERIGHTSUSER);
      argParser.addArgument(effectiveRightsUser);

      effectiveRightsAttrs = new StringArgument("effectiveRightsAttrs",
          OPTION_SHORT_EFFECTIVERIGHTSATTR, OPTION_LONG_EFFECTIVERIGHTSATTR,
          false, true, true, INFO_ATTRIBUTE_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_EFFECTIVERIGHTS_ATTR.get());
      effectiveRightsAttrs.setPropertyName(OPTION_LONG_EFFECTIVERIGHTSATTR);
      argParser.addArgument(effectiveRightsAttrs);

      version = new IntegerArgument("version", OPTION_SHORT_PROTOCOL_VERSION,
          OPTION_LONG_PROTOCOL_VERSION, false, false, true,
          INFO_PROTOCOL_VERSION_PLACEHOLDER.get(), 3, null,
          INFO_DESCRIPTION_VERSION.get());
      version.setPropertyName(OPTION_LONG_PROTOCOL_VERSION);
      argParser.addArgument(version);

      final StringArgument encodingStr = new StringArgument("encoding", 'i',
          "encoding", false, false, true, INFO_ENCODING_PLACEHOLDER.get(),
          null, null, INFO_DESCRIPTION_ENCODING.get());
      encodingStr.setPropertyName("encoding");
      argParser.addArgument(encodingStr);

      dereferencePolicy = new MultiChoiceArgument<DereferenceAliasesPolicy>(
          "derefpolicy", 'a', "dereferencePolicy", false, true,
          INFO_DEREFERENCE_POLICE_PLACEHOLDER.get(), DereferenceAliasesPolicy
              .values(), false, INFO_SEARCH_DESCRIPTION_DEREFERENCE_POLICY
              .get());
      dereferencePolicy.setPropertyName("dereferencePolicy");
      dereferencePolicy.setDefaultValue(DereferenceAliasesPolicy.NEVER);
      argParser.addArgument(dereferencePolicy);

      typesOnly = new BooleanArgument("typesOnly", 'A', "typesOnly",
          INFO_DESCRIPTION_TYPES_ONLY.get());
      typesOnly.setPropertyName("typesOnly");
      argParser.addArgument(typesOnly);

      sizeLimit = new IntegerArgument("sizeLimit", 'z', "sizeLimit", false,
          false, true, INFO_SIZE_LIMIT_PLACEHOLDER.get(), 0, null,
          INFO_SEARCH_DESCRIPTION_SIZE_LIMIT.get());
      sizeLimit.setPropertyName("sizeLimit");
      argParser.addArgument(sizeLimit);

      timeLimit = new IntegerArgument("timeLimit", 'l', "timeLimit", false,
          false, true, INFO_TIME_LIMIT_PLACEHOLDER.get(), 0, null,
          INFO_SEARCH_DESCRIPTION_TIME_LIMIT.get());
      timeLimit.setPropertyName("timeLimit");
      argParser.addArgument(timeLimit);

      dontWrap = new BooleanArgument("dontwrap", 't', "dontWrap",
          INFO_DESCRIPTION_DONT_WRAP.get());
      dontWrap.setPropertyName("dontWrap");
      argParser.addArgument(dontWrap);

      countEntries = new BooleanArgument("countentries", null, "countEntries",
          INFO_DESCRIPTION_COUNT_ENTRIES.get());
      countEntries.setPropertyName("countEntries");
      argParser.addArgument(countEntries);

      final BooleanArgument continueOnError = new BooleanArgument(
          "continueOnError", 'c', "continueOnError",
          INFO_DESCRIPTION_CONTINUE_ON_ERROR.get());
      continueOnError.setPropertyName("continueOnError");
      argParser.addArgument(continueOnError);

      noop = new BooleanArgument("noop", OPTION_SHORT_DRYRUN,
          OPTION_LONG_DRYRUN, INFO_DESCRIPTION_NOOP.get());
      noop.setPropertyName(OPTION_LONG_DRYRUN);
      argParser.addArgument(noop);

      verbose = new BooleanArgument("verbose", 'v', "verbose",
          INFO_DESCRIPTION_VERBOSE.get());
      verbose.setPropertyName("verbose");
      argParser.addArgument(verbose);

      final BooleanArgument showUsage = new BooleanArgument("showUsage",
          OPTION_SHORT_HELP, OPTION_LONG_HELP, INFO_DESCRIPTION_SHOWUSAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, getOutputStream());
    }
    catch (final ArgumentException ae)
    {
      final LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae
          .getMessage());
      println(message);
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
      connectionFactory.validate();
    }
    catch (final ArgumentException ae)
    {
      final LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae
          .getMessage());
      println(message);
      println(argParser.getUsageMessage());
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    final List<Filter> filters = new LinkedList<Filter>();
    final List<String> attributes = new LinkedList<String>();
    final ArrayList<String> filterAndAttributeStrings = argParser
        .getTrailingArguments();
    if (filterAndAttributeStrings.size() > 0)
    {
      // the list of trailing arguments should be structured as follow:
      // - If a filter file is present, trailing arguments are
      // considered as attributes
      // - If filter file is not present, the first trailing argument is
      // considered the filter, the other as attributes.
      if (!filename.isPresent())
      {
        final String filterString = filterAndAttributeStrings.remove(0);

        try
        {
          filters.add(Filter.valueOf(filterString));
        }
        catch (final LocalizedIllegalArgumentException e)
        {
          println(e.getMessageObject());
          return ResultCode.CLIENT_SIDE_FILTER_ERROR.intValue();
        }
      }
      // The rest are attributes
      for (final String s : filterAndAttributeStrings)
      {
        attributes.add(s);
      }
    }

    if (filename.isPresent())
    {
      // Read the filter strings.
      BufferedReader in = null;
      try
      {
        in = new BufferedReader(new FileReader(filename.getValue()));
        String line = null;

        while ((line = in.readLine()) != null)
        {
          if (line.trim().equals(""))
          {
            // ignore empty lines.
            continue;
          }
          final Filter ldapFilter = Filter.valueOf(line);
          filters.add(ldapFilter);
        }
      }
      catch (final LocalizedIllegalArgumentException e)
      {
        println(e.getMessageObject());
        return ResultCode.CLIENT_SIDE_FILTER_ERROR.intValue();
      }
      catch (final IOException e)
      {
        println(LocalizableMessage.raw(e.toString()));
        return ResultCode.CLIENT_SIDE_FILTER_ERROR.intValue();
      }
      finally
      {
        if (in != null)
        {
          try
          {
            in.close();
          }
          catch (final IOException ioe)
          {
          }
        }
      }
    }

    if (filters.isEmpty())
    {
      println(ERR_SEARCH_NO_FILTERS.get());
      println(argParser.getUsageMessage());
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    SearchScope scope;
    try
    {
      scope = searchScope.getTypedValue();
    }
    catch (final ArgumentException ex1)
    {
      println(ex1.getMessageObject());
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    SearchRequest search;
    try
    {
      search = Requests.newSearchRequest(DN.valueOf(baseDN.getValue()), scope,
          filters.get(0), attributes.toArray(new String[attributes.size()]));
    }
    catch (final LocalizedIllegalArgumentException e)
    {
      println(e.getMessageObject());
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    // Read the LDAP version number.
    try
    {
      final int versionNumber = version.getIntValue();
      if (versionNumber != 2 && versionNumber != 3)
      {
        println(ERR_DESCRIPTION_INVALID_VERSION.get(String
            .valueOf(versionNumber)));
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }
    }
    catch (final ArgumentException ae)
    {
      println(ERR_DESCRIPTION_INVALID_VERSION.get(String.valueOf(version
          .getValue())));
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    search.setTypesOnly(typesOnly.isPresent());
    // searchOptions.setShowOperations(noop.isPresent());
    // searchOptions.setVerbose(verbose.isPresent());
    // searchOptions.setContinueOnError(continueOnError.isPresent());
    // searchOptions.setEncoding(encodingStr.getValue());
    // searchOptions.setCountMatchingEntries(countEntries.isPresent());
    try
    {
      search.setTimeLimit(timeLimit.getIntValue());
      search.setSizeLimit(sizeLimit.getIntValue());
    }
    catch (final ArgumentException ex1)
    {
      println(ex1.getMessageObject());
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }
    try
    {
      search.setDereferenceAliasesPolicy(dereferencePolicy.getTypedValue());
    }
    catch (final ArgumentException ex1)
    {
      println(ex1.getMessageObject());
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    if (controlStr.isPresent())
    {
      for (final String ctrlString : controlStr.getValues())
      {
        try
        {
          final Control ctrl = Utils.getControl(ctrlString);
          search.addControl(ctrl);
        }
        catch (final DecodeException de)
        {
          final LocalizableMessage message = ERR_TOOL_INVALID_CONTROL_STRING
              .get(ctrlString);
          println(message);
          ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
      }
    }

    if (effectiveRightsUser.isPresent())
    {
      final String authzID = effectiveRightsUser.getValue();
      if (!authzID.startsWith("dn:"))
      {
        final LocalizableMessage message = ERR_EFFECTIVERIGHTS_INVALID_AUTHZID
            .get(authzID);
        println(message);
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }
      final Control effectiveRightsControl = GetEffectiveRightsRequestControl
          .newControl(false, authzID.substring(3), effectiveRightsAttrs
              .getValues().toArray(
                  new String[effectiveRightsAttrs.getValues().size()]));
      search.addControl(effectiveRightsControl);
    }

    if (proxyAuthzID.isPresent())
    {
      final Control proxyControl = ProxiedAuthV2RequestControl
          .newControl(proxyAuthzID.getValue());
      search.addControl(proxyControl);
    }

    if (pSearchInfo.isPresent())
    {
      final String infoString = StaticUtils.toLowerCase(pSearchInfo.getValue()
          .trim());
      boolean changesOnly = true;
      boolean returnECs = true;

      final StringTokenizer tokenizer = new StringTokenizer(infoString, ":");

      if (!tokenizer.hasMoreTokens())
      {
        final LocalizableMessage message = ERR_PSEARCH_MISSING_DESCRIPTOR.get();
        println(message);
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }
      else
      {
        final String token = tokenizer.nextToken();
        if (!token.equals("ps"))
        {
          final LocalizableMessage message = ERR_PSEARCH_DOESNT_START_WITH_PS
              .get(String.valueOf(infoString));
          println(message);
          return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
      }

      final ArrayList<PersistentSearchChangeType> ct = new ArrayList<PersistentSearchChangeType>(
          4);
      if (tokenizer.hasMoreTokens())
      {
        final StringTokenizer st = new StringTokenizer(tokenizer.nextToken(),
            ", ");
        if (!st.hasMoreTokens())
        {
          ct.add(PersistentSearchChangeType.ADD);
          ct.add(PersistentSearchChangeType.DELETE);
          ct.add(PersistentSearchChangeType.MODIFY);
          ct.add(PersistentSearchChangeType.MODIFY_DN);
        }
        else
        {
          do
          {
            final String token = st.nextToken();
            if (token.equals("add"))
            {
              ct.add(PersistentSearchChangeType.ADD);
            }
            else if (token.equals("delete") || token.equals("del"))
            {
              ct.add(PersistentSearchChangeType.DELETE);
            }
            else if (token.equals("modify") || token.equals("mod"))
            {
              ct.add(PersistentSearchChangeType.MODIFY);
            }
            else if (token.equals("modifydn") || token.equals("moddn")
                || token.equals("modrdn"))
            {
              ct.add(PersistentSearchChangeType.MODIFY_DN);
            }
            else if (token.equals("any") || token.equals("all"))
            {
              ct.add(PersistentSearchChangeType.ADD);
              ct.add(PersistentSearchChangeType.DELETE);
              ct.add(PersistentSearchChangeType.MODIFY);
              ct.add(PersistentSearchChangeType.MODIFY_DN);
            }
            else
            {
              final LocalizableMessage message = ERR_PSEARCH_INVALID_CHANGE_TYPE
                  .get(String.valueOf(token));
              println(message);
              return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
          }
          while (st.hasMoreTokens());
        }
      }

      if (tokenizer.hasMoreTokens())
      {
        final String token = tokenizer.nextToken();
        if (token.equals("1") || token.equals("true") || token.equals("yes"))
        {
          changesOnly = true;
        }
        else if (token.equals("0") || token.equals("false")
            || token.equals("no"))
        {
          changesOnly = false;
        }
        else
        {
          final LocalizableMessage message = ERR_PSEARCH_INVALID_CHANGESONLY
              .get(String.valueOf(token));
          println(message);
          return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
      }

      if (tokenizer.hasMoreTokens())
      {
        final String token = tokenizer.nextToken();
        if (token.equals("1") || token.equals("true") || token.equals("yes"))
        {
          returnECs = true;
        }
        else if (token.equals("0") || token.equals("false")
            || token.equals("no"))
        {
          returnECs = false;
        }
        else
        {
          final LocalizableMessage message = ERR_PSEARCH_INVALID_RETURN_ECS
              .get(String.valueOf(token));
          println(message);
          return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
      }

      final PersistentSearchRequestControl psearchControl = PersistentSearchRequestControl
          .newControl(true, changesOnly, returnECs, ct
              .toArray(new PersistentSearchChangeType[ct.size()]));
      search.addControl(psearchControl);
    }

    if (assertionFilter.isPresent())
    {
      final String filterString = assertionFilter.getValue();
      Filter filter;
      try
      {
        filter = Filter.valueOf(filterString);

        // FIXME -- Change this to the correct OID when the official one
        // is assigned.
        final Control assertionControl = AssertionRequestControl.newControl(
            true, filter);
        search.addControl(assertionControl);
      }
      catch (final LocalizedIllegalArgumentException le)
      {
        final LocalizableMessage message = ERR_LDAP_ASSERTION_INVALID_FILTER
            .get(le.getMessage());
        println(message);
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }
    }

    if (matchedValuesFilter.isPresent())
    {
      final LinkedList<String> mvFilterStrings = matchedValuesFilter
          .getValues();
      final List<Filter> mvFilters = new ArrayList<Filter>();
      for (final String s : mvFilterStrings)
      {
        try
        {
          final Filter f = Filter.valueOf(s);
          mvFilters.add(f);
        }
        catch (final LocalizedIllegalArgumentException le)
        {
          final LocalizableMessage message = ERR_LDAP_MATCHEDVALUES_INVALID_FILTER
              .get(le.getMessage());
          println(message);
          return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
      }

      final MatchedValuesRequestControl mvc = MatchedValuesRequestControl
          .newControl(true, mvFilters);
      search.addControl(mvc);
    }

    if (sortOrder.isPresent())
    {
      try
      {
        search.addControl(ServerSideSortRequestControl.newControl(false,
            sortOrder.getValue()));
      }
      catch (final LocalizedIllegalArgumentException le)
      {
        final LocalizableMessage message = ERR_LDAP_SORTCONTROL_INVALID_ORDER
            .get(le.getMessageObject());
        println(message);
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }
    }

    if (vlvDescriptor.isPresent())
    {
      if (!sortOrder.isPresent())
      {
        final LocalizableMessage message = ERR_LDAPSEARCH_VLV_REQUIRES_SORT
            .get(vlvDescriptor.getLongIdentifier(), sortOrder
                .getLongIdentifier());
        println(message);
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }

      final StringTokenizer tokenizer = new StringTokenizer(vlvDescriptor
          .getValue(), ":");
      final int numTokens = tokenizer.countTokens();
      if (numTokens == 3)
      {
        try
        {
          final int beforeCount = Integer.parseInt(tokenizer.nextToken());
          final int afterCount = Integer.parseInt(tokenizer.nextToken());
          final ByteString assertionValue = ByteString.valueOf(tokenizer
              .nextToken());
          search.addControl(VirtualListViewRequestControl.newAssertionControl(
              true, assertionValue, beforeCount, afterCount, null));
        }
        catch (final Exception e)
        {
          final LocalizableMessage message = ERR_LDAPSEARCH_VLV_INVALID_DESCRIPTOR
              .get();
          println(message);
          return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
      }
      else if (numTokens == 4)
      {
        try
        {
          final int beforeCount = Integer.parseInt(tokenizer.nextToken());
          final int afterCount = Integer.parseInt(tokenizer.nextToken());
          final int offset = Integer.parseInt(tokenizer.nextToken());
          final int contentCount = Integer.parseInt(tokenizer.nextToken());
          search.addControl(VirtualListViewRequestControl.newOffsetControl(
              true, offset, contentCount, beforeCount, afterCount, null));
        }
        catch (final Exception e)
        {
          final LocalizableMessage message = ERR_LDAPSEARCH_VLV_INVALID_DESCRIPTOR
              .get();
          println(message);
          return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
      }
      else
      {
        final LocalizableMessage message = ERR_LDAPSEARCH_VLV_INVALID_DESCRIPTOR
            .get();
        println(message);
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }
    }

    int pageSize = 0;
    if (simplePageSize.isPresent())
    {
      if (filters.size() > 1)
      {
        final LocalizableMessage message = ERR_PAGED_RESULTS_REQUIRES_SINGLE_FILTER
            .get();
        println(message);
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }

      try
      {
        pageSize = simplePageSize.getIntValue();
        search.addControl(SimplePagedResultsControl.newControl(true, pageSize,
            ByteString.empty()));
      }
      catch (final ArgumentException ae)
      {
        final LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae
            .getMessage());
        println(message);
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }
    }
    /*
     * if(connectionOptions.useSASLExternal()) { if(!connectionOptions.useSSL()
     * && !connectionOptions.useStartTLS()) { LocalizableMessage message =
     * ERR_TOOL_SASLEXTERNAL_NEEDS_SSL_OR_TLS.get();
     * err.println(wrapText(message, MAX_LINE_WIDTH)); return
     * CLIENT_SIDE_PARAM_ERROR; } if(keyStorePathValue == null) {
     * LocalizableMessage message = ERR_TOOL_SASLEXTERNAL_NEEDS_KEYSTORE.get();
     * err.println(wrapText(message, MAX_LINE_WIDTH)); return
     * CLIENT_SIDE_PARAM_ERROR; } }
     * connectionOptions.setVerbose(verbose.isPresent());
     */

    int wrapColumn = 80;
    if (dontWrap.isPresent())
    {
      wrapColumn = 0;
    }

    if (noop.isPresent())
    {
      // We don't actually need to open a connection or perform the
      // search, so we're done. We should return 0 to either mean that the
      // processing was successful or that there were no matching entries, based
      // on countEntries.isPresent() (but in either case the return value should
      // be zero).
      return 0;
    }

    Connection connection;
    try
    {
      connection = connectionFactory.getConnection();
    }
    catch (final ErrorResultException ere)
    {
      return Utils.printErrorMessage(this, ere);
    }
    catch (final InterruptedException e)
    {
      // This shouldn't happen because there are no other threads to
      // interrupt this one.
      println(LocalizableMessage.raw(e.getLocalizedMessage()));
      return ResultCode.CLIENT_SIDE_USER_CANCELLED.intValue();
    }

    Utils.printPasswordPolicyResults(this, connection);

    try
    {
      int filterIndex = 0;
      ldifWriter = new LDIFEntryWriter(getOutputStream())
          .setWrapColumn(wrapColumn);
      final LDAPSearchResultHandler resultHandler = new LDAPSearchResultHandler();
      while (true)
      {
        Result result;
        try
        {
          result = connection.search(search, resultHandler);
        }
        catch (final InterruptedException e)
        {
          // This shouldn't happen because there are no other threads to
          // interrupt this one.
          result = Responses.newResult(ResultCode.CLIENT_SIDE_USER_CANCELLED)
              .setCause(e).setDiagnosticMessage(e.getLocalizedMessage());
          throw ErrorResultException.wrap(result);
        }

        try
        {
          final ServerSideSortResponseControl control = result.getControl(
              ServerSideSortResponseControl.DECODER, new DecodeOptions());
          if (control != null)
          {
            if (control.getResult() != ResultCode.SUCCESS)
            {
              final LocalizableMessage msg = WARN_LDAPSEARCH_SORT_ERROR
                  .get(control.getResult().toString());
              println(msg);
            }
          }
        }
        catch (final DecodeException e)
        {
          println(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
        }

        try
        {
          final VirtualListViewResponseControl control = result.getControl(
              VirtualListViewResponseControl.DECODER, new DecodeOptions());
          if (control != null)
          {
            if (control.getResult() == ResultCode.SUCCESS)
            {
              LocalizableMessage msg = INFO_LDAPSEARCH_VLV_TARGET_OFFSET
                  .get(control.getTargetPosition());
              println(msg);

              msg = INFO_LDAPSEARCH_VLV_CONTENT_COUNT.get(control
                  .getContentCount());
              println(msg);
            }
            else
            {
              final LocalizableMessage msg = WARN_LDAPSEARCH_VLV_ERROR
                  .get(control.getResult().toString());
              println(msg);
            }
          }
        }
        catch (final DecodeException e)
        {
          println(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
        }

        try
        {
          SimplePagedResultsControl control = result.getControl(
              SimplePagedResultsControl.DECODER, new DecodeOptions());
          if (control != null)
          {
            if (control.getCookie().length() > 0)
            {
              if (!isQuiet())
              {
                pressReturnToContinue();
              }
              final Iterator<Control> iterator = search.getControls()
                  .iterator();
              while (iterator.hasNext())
              {
                if (iterator.next().getOID().equals(
                    SimplePagedResultsControl.OID))
                {
                  iterator.remove();
                }
              }
              control = SimplePagedResultsControl.newControl(true, pageSize,
                  control.getCookie());
              search.addControl(control);
              continue;
            }
          }
        }
        catch (final DecodeException e)
        {
          println(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
        }

        println();
        println(ERR_TOOL_RESULT_CODE.get(result.getResultCode().intValue(),
            result.getResultCode().toString()));
        if ((result.getDiagnosticMessage() != null)
            && (result.getDiagnosticMessage().length() > 0))
        {
          println(LocalizableMessage.raw(result.getDiagnosticMessage()));
        }
        if (result.getMatchedDN() != null && result.getMatchedDN().length() > 0)
        {
          println(ERR_TOOL_MATCHED_DN.get(result.getMatchedDN()));
        }

        filterIndex++;
        if (filterIndex < filters.size())
        {
          search.setFilter(filters.get(filterIndex));
        }
        else
        {
          break;
        }
      }
      if (countEntries.isPresent() && !isQuiet())
      {
        final LocalizableMessage message = INFO_LDAPSEARCH_MATCHING_ENTRY_COUNT
            .get(resultHandler.entryCount);
        println(message);
        println();
      }
    }
    catch (final ErrorResultException ere)
    {
      return Utils.printErrorMessage(this, ere);
    }
    finally
    {
      connection.close();
    }

    return 0;
  }
}
