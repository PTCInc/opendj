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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.types.operation;

import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.RawFilter;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultReference;

/**
 * This class defines a set of methods that are available for use by
 * pre-operation plugins for search operations.  Note that this
 * interface is intended only to define an API for use by plugins and
 * is not intended to be implemented by any custom classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface PreOperationSearchOperation
       extends PreOperationOperation
{
  /**
   * Retrieves the raw, unprocessed base DN as included in the request
   * from the client.  This may or may not contain a valid DN, as no
   * validation will have been performed.
   *
   * @return  The raw, unprocessed base DN as included in the request
   *          from the client.
   */
  ByteString getRawBaseDN();



  /**
   * Retrieves the base DN for this search operation.
   *
   * @return  The base DN for this search operation.
   */
  DN getBaseDN();



  /**
   * Retrieves the scope for this search operation.
   *
   * @return  The scope for this search operation.
   */
  SearchScope getScope();



  /**
   * Retrieves the alias dereferencing policy for this search
   * operation.
   *
   * @return  The alias dereferencing policy for this search
   *          operation.
   */
  DereferenceAliasesPolicy getDerefPolicy();



  /**
   * Retrieves the size limit for this search operation.
   *
   * @return  The size limit for this search operation.
   */
  int getSizeLimit();



  /**
   * Retrieves the time limit for this search operation.
   *
   * @return  The time limit for this search operation.
   */
  int getTimeLimit();



  /**
   * Retrieves the typesOnly flag for this search operation.
   *
   * @return  The typesOnly flag for this search operation.
   */
  boolean getTypesOnly();



  /**
   * Retrieves the raw, unprocessed search filter as included in the
   * request from the client.  It may or may not contain a valid
   * filter (e.g., unsupported attribute types or values with an
   * invalid syntax) because no validation will have been performed on
   * it.
   *
   * @return  The raw, unprocessed search filter as included in the
   *          request from the client.
   */
  RawFilter getRawFilter();



  /**
   * Retrieves the filter for this search operation.
   *
   * @return  The filter for this search operation.
   */
  SearchFilter getFilter();



  /**
   * Retrieves the set of requested attributes for this search
   * operation.  Its contents should not be altered.
   *
   * @return  The set of requested attributes for this search
   *          operation.
   */
  Set<String> getAttributes();



  /**
   * Returns the provided entry to the client.
   *
   * @param  entry     The entry that should be returned.
   * @param  controls  The set of controls to include with the entry
   *                   (may be {@code null} if no controls should be
   *                   included with the entry).
   *
   * @return  {@code true} if the caller should continue processing
   *          the search request and sending additional entries and
   *          references, or {@code false} if not for some reason
   *          (e.g., the size limit has been reached or the search has
   *          been abandoned).
   */
  boolean returnEntry(Entry entry, List<Control> controls);



  /**
   * Returns the provided search result reference to the client.
   *
   * @param   dn        A DN related to the specified search
   *                    reference.
   * @param  reference  The search reference that should be returned.
   *
   * @return  {@code true} if the caller should continue processing
   *          the search request and sending additional entries and
   *          references, or {@code false} if not for some reason
   *          (e.g., the size limit has been reached or the search has
   *          been abandoned).
   */
  boolean returnReference(DN dn ,SearchResultReference reference);
}

