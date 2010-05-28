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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import java.util.concurrent.ExecutionException;

import org.opends.sdk.responses.Result;



/**
 * Thrown when the result code returned in a Result indicates that the Request
 * was unsuccessful.
 */
@SuppressWarnings("serial")
public class ErrorResultException extends ExecutionException
{
  /**
   * Wraps the provided result in an appropriate error result exception. The
   * type of error result exception used depends on the underlying result code.
   *
   * @param result
   *          The result whose result code indicates a failure.
   * @return The error result exception wrapping the provided result.
   * @throws IllegalArgumentException
   *           If the provided result does not represent a failure.
   * @throws NullPointerException
   *           If {@code result} was {@code null}.
   */
  public static ErrorResultException wrap(final Result result)
      throws IllegalArgumentException, NullPointerException
  {
    if (!result.getResultCode().isExceptional())
    {
      throw new IllegalArgumentException(
          "Attempted to wrap a successful result: " + result);
    }

    ResultCode rc = result.getResultCode();
    if (rc == ResultCode.ASSERTION_FAILED)
    {
      return new AssertionFailureException(result);
    }
    else if (rc == ResultCode.AUTH_METHOD_NOT_SUPPORTED
        || rc == ResultCode.CLIENT_SIDE_AUTH_UNKNOWN
        || rc == ResultCode.INAPPROPRIATE_AUTHENTICATION
        || rc == ResultCode.INVALID_CREDENTIALS)
    {
      return new AuthenticationException(result);
    }
    else if (rc == ResultCode.AUTHORIZATION_DENIED
        || rc == ResultCode.CONFIDENTIALITY_REQUIRED
        || rc == ResultCode.INSUFFICIENT_ACCESS_RIGHTS
        || rc == ResultCode.STRONG_AUTH_REQUIRED)
    {
      return new AuthorizationException(result);
    }
    else if (rc == ResultCode.CLIENT_SIDE_USER_CANCELLED
        || rc == ResultCode.CANCELLED)
    {
      return new CancelledResultException(result);
    }
    else if (rc == ResultCode.CLIENT_SIDE_SERVER_DOWN
        || rc == ResultCode.CLIENT_SIDE_CONNECT_ERROR
        || rc == ResultCode.CLIENT_SIDE_DECODING_ERROR
        || rc == ResultCode.CLIENT_SIDE_ENCODING_ERROR)
    {
      return new ConnectionException(result);
    }
    else if (rc == ResultCode.ATTRIBUTE_OR_VALUE_EXISTS
        || rc == ResultCode.CONSTRAINT_VIOLATION
        || rc == ResultCode.ENTRY_ALREADY_EXISTS
        || rc == ResultCode.INVALID_ATTRIBUTE_SYNTAX
        || rc == ResultCode.INVALID_DN_SYNTAX
        || rc == ResultCode.NAMING_VIOLATION
        || rc == ResultCode.NOT_ALLOWED_ON_NONLEAF
        || rc == ResultCode.NOT_ALLOWED_ON_RDN
        || rc == ResultCode.OBJECTCLASS_MODS_PROHIBITED
        || rc == ResultCode.OBJECTCLASS_VIOLATION
        || rc == ResultCode.UNDEFINED_ATTRIBUTE_TYPE)
    {
      return new ConstraintViolationException(result);
    }
    else if (rc == ResultCode.REFERRAL)
    {
      return new ReferralException(result);
    }
    else if (rc == ResultCode.NO_SUCH_OBJECT
        || rc == ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED)
    {
      return new EntryNotFoundException(result);
    }
    else if (rc == ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED)
    {
      return new MultipleEntriesFoundException(result);
    }
    else if (rc == ResultCode.CLIENT_SIDE_TIMEOUT
        || rc == ResultCode.TIME_LIMIT_EXCEEDED)
    {
      return new TimeoutResultException(result);
    }

    return new ErrorResultException(result);
  }



  private final Result result;



  /**
   * Creates a new error result exception using the provided result.
   *
   * @param result
   *          The error result.
   */
  ErrorResultException(final Result result)
  {
    super(result.getResultCode() + ": " + result.getDiagnosticMessage());
    this.result = result;
  }



  /**
   * Returns the error result which caused this exception to be thrown. The type
   * of result returned corresponds to the expected result type of the original
   * request.
   *
   * @return The error result which caused this exception to be thrown.
   */
  public Result getResult()
  {
    return result;
  }
}
