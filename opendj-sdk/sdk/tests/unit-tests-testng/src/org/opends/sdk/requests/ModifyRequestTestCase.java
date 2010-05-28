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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk.requests;



import org.opends.sdk.DN;
import org.opends.sdk.ModificationType;
import org.testng.annotations.DataProvider;



/**
 * Tests the modify request.
 */
public class ModifyRequestTestCase extends RequestTestCase
{
  @DataProvider(name = "ModifyRequests")
  public Object[][] getModifyRequests() throws Exception
  {
    final ModifyRequest[] requests = {
        Requests.newModifyRequest(DN.valueOf("uid=Modifyrequest1"))
            .addModification(ModificationType.ADD, "userpassword", "password"),
        Requests.newModifyRequest("cn=Modifyrequesttestcase").addModification(
            ModificationType.ADD, "userpassword", "password"),
        Requests.newModifyRequest("dn: ou=People,o=test", "changetype: modify",
            "add: userpassword", "userpassword: password") };
    final Object[][] objArray = new Object[requests.length][1];
    for (int i = 0; i < requests.length; i++)
    {
      objArray[i][0] = requests[i];
    }
    return objArray;
  }



  @Override
  protected ModifyRequest[] createTestRequests() throws Exception
  {
    final Object[][] objs = getModifyRequests();
    final ModifyRequest[] ops = new ModifyRequest[objs.length];
    for (int i = 0; i < objs.length; i++)
    {
      ops[i] = (ModifyRequest) objs[i][0];
    }
    return ops;
  }

}
