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
 */
package net.sf.regain.search.sharedlib.hit;

import net.sf.regain.RegainException;
import net.sf.regain.RegainToolkit;
import net.sf.regain.search.SearchToolkit;
import net.sf.regain.search.results.SearchResults;
import net.sf.regain.util.sharedtag.PageRequest;
import net.sf.regain.util.sharedtag.PageResponse;

import org.apache.lucene.document.Document;

/**
 * Generates a link to the current hit's document. For the link text the title
 * is used.
 * <p>
 * Tag Parameters:
 * <ul>
 * <li><code>class</code>: The style sheet class to use for the link.</li>
 * </ul>
 *
 * @author Til Schneider, www.murfman.de
 */
public class LinkTag extends AbstractHitTag {

  /**
   * Generates the tag.
   *
   * @param request The page request.
   * @param response The page response.
   * @param hit The current search hit.
   * @param hitIndex The index of the hit.
   * @throws RegainException If there was an exception.
   */
  protected void printEndTag(PageRequest request, PageResponse response,
    Document hit, int hitIndex)
    throws RegainException
  {
    // Get the search results
    SearchResults results = SearchToolkit.getSearchResults(request);
    boolean shouldHighlight = results.getShouldHighlight(hitIndex);
    boolean onlyUrl = getParameterAsBoolean("onlyUrl", false);

    String url = results.getHitUrl(hitIndex);
    boolean openInNewWindow = results.getOpenHitInNewWindow(hitIndex);

    // Pass file URLs to the file servlet
    String href = url;
    String encoding = response.getEncoding();
    boolean useFileToHttpBridge = results.getUseFileToHttpBridgeForHit(hitIndex);
    if (url.startsWith("file://") && useFileToHttpBridge) {
      href = SearchToolkit.encodeFileUrl(url, encoding);

      // Add the index name
      // NOTE: This is needed to ensure that only documents can be loaded that
      //       are in the indexes
      String indexName = results.getHitIndexName(hitIndex);
      // @todo: refactor this
      //String encodedIndexName = RegainToolkit.urlEncode(indexName, encoding);
      //href += "?index=" + encodedIndexName;
    } else {
      href = RegainToolkit.urlDecode(url, RegainToolkit.INDEX_ENCODING);

      // For IE we have to encode "%" to "%25" again.
      // Otherwise it will search files having a real "%20" in their name as " ".
      href = RegainToolkit.replace(href, "%", "%25");
    }

    if (onlyUrl)
    {
      response.print(href);
      return;
    }
    // Generate the link
    response.print("<a href=\"" + href + "\"");
    if (openInNewWindow) {
      response.print(" target=\"_blank\"");
    }
    String styleSheetClass = getParameter("class");
    if (styleSheetClass != null) {
      response.print(" class=\"" + styleSheetClass + "\"");
    }
    response.print(">");

    String title = linkTitle(hit, shouldHighlight, url);
    if(shouldHighlight)
      response.print(title);
    else
      response.printNoHtml(title);
    response.print("</a>");
  }

  protected String linkTitle(Document hit, boolean shouldHighlight, String url)
  throws RegainException {
	  String fieldName = (shouldHighlight) ? "highlightedTitle" : "title";
	  String title = hit.get(fieldName);
	  if (shouldHighlight && (title == null || title.length() == 0)) {
		  title = hit.get("title");
	  }

	  // Trim the title
	  if (title != null) {
		  title = title.trim();
	  }

	  // Use the URL as title if there is no title.
	  if ((title == null) || (title.length() == 0)) {
		  int lastSlash = url.lastIndexOf("/");
		  if (lastSlash == -1) {
			  title = url;
		  } else {
			  title = RegainToolkit.urlDecode(url.substring(lastSlash + 1), RegainToolkit.INDEX_ENCODING);
		  }
	  }
	  return title;
  }

}
