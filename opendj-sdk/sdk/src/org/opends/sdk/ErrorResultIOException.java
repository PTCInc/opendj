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

package org.opends.sdk;



import java.io.IOException;

import com.sun.opends.sdk.util.Validator;



/**
 * An {@code ErrorResultIOException} adapts an {@code ErrorResultException} to
 * an {@code IOException}.
 */
@SuppressWarnings("serial")
public final class ErrorResultIOException extends IOException
{
  private final ErrorResultException cause;



  /**
   * Creates a new error result IO exception with the provided cause.
   *
   * @param cause
   *          The cause which may be later retrieved by the {@link #getCause}
   *          method.
   * @throws NullPointerException
   *           If {@code cause} was {@code null}.
   */
  public ErrorResultIOException(final ErrorResultException cause)
      throws NullPointerException
  {
    super(Validator.ensureNotNull(cause));

    this.cause = cause;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ErrorResultException getCause()
  {
    return cause;
  }
}
