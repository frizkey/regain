/*
 * regain - A file search engine providing plenty of formats
 * Copyright (C) 2004  Til Schneider
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Contact: Til Schneider, info@murfman.de
 *
 * CVS information:
 *  $RCSfile$
 *   $Source$
 *     $Date: 2005-03-30 12:30:03 +0200 (Mi, 30 Mrz 2005) $
 *   $Author: til132 $
 * $Revision: 111 $
 */
package net.sf.regain.search.sharedlib.error;

import net.sf.regain.RegainException;
import net.sf.regain.util.sharedtag.PageRequest;
import net.sf.regain.util.sharedtag.PageResponse;
import net.sf.regain.util.sharedtag.SharedTag;

/**
 * Generates the error message.
 *
 * @author Til Schneider, www.murfman.de
 */
public abstract class AbstractErrorTag extends SharedTag {

  /**
   * Called when the parser reaches the end tag.
   *  
   * @param request The page request.
   * @param response The page response.
   * @throws RegainException If there was an exception.
   */
  public final void printEndTag(PageRequest request, PageResponse response)
    throws RegainException
  {
    Throwable error = (Throwable) request.getContextAttribute("page.exception");
    printEndTag(request, response, error);
  }


  /**
   * Called when the parser reaches the end tag.
   *  
   * @param request The page request.
   * @param response The page response.
   * @param error The error of the request.
   * @throws RegainException If there was an exception.
   */
  protected abstract void printEndTag(PageRequest request,
    PageResponse response, Throwable error)
    throws RegainException;
  
}
