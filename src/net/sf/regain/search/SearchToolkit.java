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
package net.sf.regain.search;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.zip.DataFormatException;

import net.sf.regain.RegainException;
import net.sf.regain.RegainToolkit;
import net.sf.regain.search.access.SearchAccessController;
import net.sf.regain.search.config.DefaultSearchConfigFactory;
import net.sf.regain.search.config.IndexConfig;
import net.sf.regain.search.config.SearchConfig;
import net.sf.regain.search.config.SearchConfigFactory;
import net.sf.regain.search.results.SearchResults;
import net.sf.regain.search.results.SearchResultsImpl;
import net.sf.regain.util.sharedtag.PageRequest;
import net.sf.regain.util.sharedtag.PageResponse;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.document.CompressionTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollector;

/**
 * A toolkit for the search JSPs containing helper methods.
 *
 * @author Til Schneider, www.murfman.de
 */
public class SearchToolkit {

  /**
   * The name of the page context attribute that holds the search query.
   */
  private static final String SEARCH_QUERY_CONTEXT_ATTR_NAME = "SearchQuery";
  /**
   * The name of the page context attribute that holds the SearchResults.
   */
  private static final String SEARCH_RESULTS_ATTR_NAME = "SearchResults";
  /**
   * The name of the page context attribute that holds the IndexConfig array.
   */
  private static final String INDEX_CONFIG_CONTEXT_ARRAY_ATTR_NAME = "IndexConfigArr";
  /**
   * The prefix for request parameters that contain additional field values.
   */
  private static final String FIELD_PREFIX = "field.";
  /**
   * The prefix for request parameters that contain additional field values that
   * is not a string.
   */
  private static final String FIELD_PREFIX_NOSTRING = "fieldNoString.";
  /**
   * The configuration of the search mask.
   */
  private static SearchConfig mConfig;
  /**
   * Holds for an extension the mime type.
   */
  private static HashMap<String, String> mMimeTypeHash;

  /**
   * Gets the IndexConfig array from the PageContext. It contains the
   * configurations of all indexes the search query is searching on.
   * <p>
   * If there is no IndexConfig array in the PageContext it is put in the
   * PageContext, so the next call will find it.
   *
   * @param request The page request where the IndexConfig array will be taken from or put to.
   * @return The IndexConfig array for the page the context is for.
   * @throws RegainException If there is no IndexConfig for the specified index.
   */
  public static IndexConfig[] getIndexConfigArr(PageRequest request)
          throws RegainException {
    IndexConfig[] configArr = (IndexConfig[]) request.getContextAttribute(INDEX_CONFIG_CONTEXT_ARRAY_ATTR_NAME);
    if (configArr == null) {
      // Load the config (if not yet done)
      loadConfiguration(request);

      // Get the names of the indexes
      String[] indexNameArr = request.getParameters("index");
      if (indexNameArr == null) {
        // There was no index specified -> Check whether we have default indexes
        // defined
        indexNameArr = mConfig.getDefaultIndexNameArr();
        if (indexNameArr == null) {
          throw new RegainException("Request parameter 'index' not specified and "
                  + "no default index configured");
        }
      }

      // Get the configurations for these indexes
      configArr = new IndexConfig[indexNameArr.length];
      for (int i = 0; i < indexNameArr.length; i++) {
        configArr[i] = mConfig.getIndexConfig(indexNameArr[i]);
        if (configArr[i] == null) {
          throw new RegainException("The configuration does not contain the index '"
                  + indexNameArr[i] + "'");
        }
      }

      // Store the IndexConfig in the page context
      request.setContextAttribute(INDEX_CONFIG_CONTEXT_ARRAY_ATTR_NAME, configArr);
    }
    return configArr;
  }

  /**
   * Gets the IndexConfig array from the PageContext. It contains the
   * configurations of all indexes the search query is searching on.
   * <p>
   * If there is no IndexConfig array in the PageContext it is put in the
   * PageContext, so the next call will find it.
   *
   * @param request The page request where the IndexConfig array will be taken  from or put to.
   * @return The IndexConfig array for the page the context is for.
   * @throws RegainException If there is no IndexConfig for the specified index.
   */
  public static IndexConfig[] getIndexConfigArrWithParent(PageRequest request) throws RegainException {
    IndexConfig[] configArr = (IndexConfig[]) request.getContextAttribute(INDEX_CONFIG_CONTEXT_ARRAY_ATTR_NAME);
    if (configArr == null) {
      // Load the config (if not yet done)
      loadConfiguration(request);

      // Get the names of the indexes
      String[] indexNameArr = request.getParameters("index");
      if (indexNameArr == null) {
        // There was no index specified -> Check whether we have default
        // indexes
        // defined
        indexNameArr = mConfig.getDefaultIndexNameArr();
        if (indexNameArr == null) {
          throw new RegainException("Request parameter 'index' not specified and "
                  + "no default index configured");
        }
      }

      // Get the configurations for these indexes
      List<IndexConfig> configList = new ArrayList<IndexConfig>();
      for (int i = 0; i < indexNameArr.length; i++) {
        IndexConfig index = mConfig.getIndexConfig(indexNameArr[i]);
        if (index == null) {
          throw new RegainException("The configuration does not contain the index '" + indexNameArr[i] + "'");

        }
        // If index is a parent index -> get all childs
        if (index.isParent()) {
          String[] allIndexName = mConfig.getAllIndexNameArr();
          for (int j = 0; j < allIndexName.length; j++) {
            IndexConfig indexParent = mConfig.getIndexConfig(allIndexName[j]);
            if (indexParent.hasParent() && indexNameArr[i].equals(indexParent.getParentName())) {
              configList.add(indexParent);
            }
          }
        } else {
          configList.add(index);
        }
      }
      // Rebuild array from list
      configArr = new IndexConfig[configList.size()];
      configList.toArray(configArr);

      // Store the IndexConfig in the page context
      request.setContextAttribute(INDEX_CONFIG_CONTEXT_ARRAY_ATTR_NAME, configArr);
    }
    return configArr;
  }

  /**
   * Gets the IndexConfig array from the configurationn. It contains the
   * configurations of all indexes in the configuration file.
   * <p>
   *
   * @return The IndexConfig array for all indizes.
   * @throws RegainException If there is no IndexConfig for the specified index.
   */
  public static IndexConfig[] getAllIndexConfigArr(PageRequest request) throws RegainException {
    loadConfiguration(request);
    String[] indexNameArr;
    indexNameArr = mConfig.getAllIndexNameArr();
    if (indexNameArr == null) {
      throw new RegainException("There are no Indizes defined in the search configuration  "
              + "no index configured");
    }

    // Get the configurations for these indexes
    IndexConfig[] configArr = new IndexConfig[indexNameArr.length];
    for (int i = 0; i < indexNameArr.length; i++) {
      configArr[i] = mConfig.getIndexConfig(indexNameArr[i]);
      if (configArr[i] == null) {
        throw new RegainException("The configuration does not contain the index '" + indexNameArr[i] + "'");
      }
    }
    return configArr;
  }

  /**
   * Gets the search query.
   *
   * @param request The request to get the query from.
   * @return The search query.
   * @throws RegainException If getting the query failed.
   */
  public static String getSearchQuery(PageRequest request)
          throws RegainException {
    String queryString = (String) request.getContextAttribute(SEARCH_QUERY_CONTEXT_ATTR_NAME);
    if (queryString == null) {
      // Get the query parameter
      StringBuilder query = new StringBuilder();
      String[] queryParamArr = request.getParametersNotNull("query");
      for (int i = 0; i < queryParamArr.length; i++) {
        if (queryParamArr[i] != null) {
          if (i != 0) {
            query.append(" ");
          }
          query.append(queryParamArr[i]);
        }
      }

      // Append the additional fields to the query
      Enumeration enm = request.getParameterNames();
      while (enm.hasMoreElements()) {
        String paramName = (String) enm.nextElement();
        if (paramName.startsWith(FIELD_PREFIX)) {
          // This is an additional field -> Append it to the query
          String fieldName = paramName.substring(FIELD_PREFIX.length());
          String fieldValue = request.getParameter(paramName);

          if (fieldValue != null) {
            fieldValue = fieldValue.trim();
            if (fieldValue.length() != 0) {
              query.append(" ");
              query.append(fieldName);
              query.append(":\"");
              query.append(fieldValue);
              query.append("\"");
            }
          }
        }
        if (paramName.startsWith(FIELD_PREFIX_NOSTRING)) {
          // This is an additional field -> Append it to the query
          String fieldName = paramName.substring(FIELD_PREFIX_NOSTRING.length());
          String fieldValue = request.getParameter(paramName);

          if (fieldValue != null) {
            fieldValue = fieldValue.trim();
            if (fieldValue.length() != 0) {
              query.append(" ");
              query.append(fieldName);
              query.append(":");
              query.append(fieldValue);
            }
          }
        }
      }

      queryString = query.toString().trim();
      request.setContextAttribute(SEARCH_QUERY_CONTEXT_ATTR_NAME, queryString);
    }

    return queryString;
  }

  /**
   * Gets the SearchResults from the PageContext.
   * <p>
   * If there is no SearchResults in the PageContext it is created and put in
   * the PageContext, so the next call will find it.
   *
   * @param request The page request where the SearchResults will be taken from or put to.
   * @return The SearchResults for the page the context is for.
   * @throws RegainException If the SearchResults could not be created.
   * @see SearchResults
   */
  public static SearchResults getSearchResults(PageRequest request)
          throws RegainException {
    SearchResults results = (SearchResults) request.getContextAttribute(SEARCH_RESULTS_ATTR_NAME);
    if (results == null) {
      // Get the index configurations
      IndexConfig[] indexConfigArr = getIndexConfigArrWithParent(request);

      results = new SearchResultsImpl(indexConfigArr, request);

      // Store the SearchResults in the page context
      request.setContextAttribute(SEARCH_RESULTS_ATTR_NAME, results);
    }

    return results;
  }

  /**
   * Extracts the file URL from a request path.
   *
   * @param requestPath The request path to extract the file URL from.
   * @param encoding The encoding to use for the URL-docoding of the requestPath.
   * @return The extracted file URL.
   * @throws RegainException If extracting the file URL failed.
   *
   * @see net.sf.regain.search.sharedlib.hit.LinkTag
   */
  public static String extractFileUrl(String requestPath, String encoding)
          throws RegainException {
    // NOTE: This is the counterpart to encodeFileUrl
    // NOTE: Removing index GET Parameter not nessesary: We already have the requestPath

    // Decode the URL
    String decodedHref = RegainToolkit.urlDecode(requestPath, encoding);

    // Cut off "http://domain/file/"
    int filePos = decodedHref.indexOf("file/");
    String fileName = decodedHref.substring(filePos + 5);


    // Restore the double slashes
    fileName = RegainToolkit.replace(fileName,
            new String[]{"$/$", "$$"},
            new String[]{"/", "$"});

    // Assemble the file URL
    return RegainToolkit.fileNameToUrl(fileName);
  }

  /**
   * Create a URL that targets the file-to-http-bridge Counterpart to extractFileUrl
   *
   * @param url	URL of the file that should be encoded
   * @param encoding Character encoding to use
   * @return Encoded File URL
   * @throws RegainException If encoding the file URL failed.
   */
  public static String encodeFileUrl(String url, String encoding) throws RegainException {
    // Get the file name
    String fileName = RegainToolkit.urlToFileName(url);

    // Workaround: Double slashes have to be prevented, because tomcat
    // merges two slashes to one (even if one of them is URL-encoded and even
    // if one of them is a backslash or an encoded backslash)
    // -> We escape the second slashe with "$/$" and normal "$" with "$$"
    //    (This should work in all cases: "a//b" -> "a/$/$b",
    //    "a///b" -> "a/$/$/b", "a$b" -> "a$$b", "a$/$b" -> "a$$/$$b")
    String decodedHref = RegainToolkit.replace("file/" + fileName,
            new String[]{"//", "$"},
            new String[]{"/$/$", "$$"});

    // Create a URL (encoded with the page encoding)
    String href = RegainToolkit.urlEncode(decodedHref, encoding);

    // Now decode the forward slashes
    // NOTE: This step is only for beautifing the URL, the above workaround is
    //       also necessary without this step
    href = RegainToolkit.replace(href, "%2F", "/");

    return href;
  }

  /**
   * Decides whether the remote access to a file should be allowed.
   * <p>
   * The access is granted if the file is in the index. The access is never
   * granted for indexes that have an access controller.
   *
   * @param request The request that holds the used index.
   * @param fileUrl The URL to file to check.
   * @return Whether the remote access to a file should be allowed.
   * @throws RegainException If checking the file failed.
   */
  public static boolean allowFileAccess(PageRequest request, String fileUrl)
          throws RegainException {
    IndexConfig[] configArr = getIndexConfigArr(request);
    Query query = null;

    IndexSearcherManager manager = null;
    IndexSearcher searcher = null;
    int nbHits = 0;
    // Check whether one of the indexes contains the file
    for (int i = 0; i < configArr.length; i++) {
      String dir = configArr[i].getDirectory();
      manager = IndexSearcherManager.getInstance(dir);

      String transformedFileUrl = fileUrl;
      // back transform the file url according to given rewrite rules
      String[][] rewriteRules = configArr[i].getRewriteRules();
      if (rewriteRules != null) {
        for (int ii = 0; ii < rewriteRules.length; ii++) {
          String[] rule = rewriteRules[ii];
          String prefix = rule[1];
          if (fileUrl.startsWith(prefix)) {
            String replacement = rule[0];
            transformedFileUrl = replacement + fileUrl.substring(prefix.length());
            break;
          }
        }
      }

      // Check whether the document is in the index
      Analyzer analyzer = new WhitespaceAnalyzer(RegainToolkit.getLuceneVersion());
      QueryParser parser = new QueryParser(RegainToolkit.getLuceneVersion(), "url", analyzer);
      String queryString = "\"" + transformedFileUrl + "\"";

      try {
        query = parser.parse(queryString);
      } catch (ParseException ex) {
        throw new RegainException("Parsing of url lookup-query failed.", ex);
      }
      if (configArr[i].getSearchAccessController() != null) {
        SearchAccessController accessController = configArr[i].getSearchAccessController();
        String[] allGroups = accessController.getUserGroups(request);
        RegainToolkit.checkGroupArray(accessController, allGroups);
        query = addAccessControlToQuery(query, allGroups);
      }

      try {
        searcher = manager.getIndexSearcher();

        TopScoreDocCollector collector = TopScoreDocCollector.create(1, false);
        searcher.search(query, collector);
        nbHits = collector.getTotalHits();
      } catch (IOException exc) {
        throw new RegainException("Searching query failed", exc);
      } finally {
        manager.releaseIndexSearcher(searcher);
      }

      // Allow the access if we found the file in the index
      if (nbHits > 0) {
        return true;
      }
    }

    // We didn't find the file in the indexes -> File access is not allowed
    return false;
  }

  /**
   * Restrict query: only allow documents that have one group of allGroups (To be used together with SearchAccessController)
   *
   * @param query Query to be modified
   * @param allGroups Groups of the user
   * @return Modified Query
   */
  public static BooleanQuery addAccessControlToQuery(Query query, String[] allGroups) {
    // Create a query that matches any group
    BooleanQuery groupQuery = new BooleanQuery();

    // Not very logical behaviour, in my opinion: If no groups are returned by the SearchAccessController, all files are shown.
    // However, if one of the Controllers returns a group, then suddenly this super-admin-capability vanished.
    // Maybe allow "null" as Super-Admin, "empty array" as No-Permissions-At-All?
    if ((allGroups == null || allGroups.length == 0)) {
      if (query instanceof BooleanQuery) {
        return (BooleanQuery) query;
      } else {
        groupQuery.add(query, Occur.MUST);
        return groupQuery;
      }
    }

    for (String group : allGroups) {
      // Add as OR
      groupQuery.add(new TermQuery(new Term(RegainToolkit.FIELD_ACCESS_CONTROL_GROUPS, group)),
              Occur.SHOULD);
    }

    // Create a main query that contains the group query and the search query
    // combined with AND
    BooleanQuery mainQuery = new BooleanQuery();
    mainQuery.add(query, Occur.MUST);
    mainQuery.add(groupQuery, Occur.MUST);

    // Set the main query as query to use
    return mainQuery;
  }

  /**
   * Sends a file to the client.
   *
   * @param request The request.
   * @param response The response.
   * @param file The file to send.
   * @throws RegainException If sending the file failed.
   */
  public static void sendFile(PageRequest request, PageResponse response, File file)
          throws RegainException {
    long lastModified = file.lastModified();
    if (lastModified < request.getHeaderAsDate("If-Modified-Since")) {
      // The browser can use the cached file
      response.sendError(304);
    } else {
      response.setHeaderAsDate("Date", System.currentTimeMillis());
      response.setHeaderAsDate("Last-Modified", lastModified);

      // TODO: Make this configurable
      if (mMimeTypeHash == null) {
        // Source: http://de.selfhtml.org/diverses/mimetypen.htm
        HashMap<String, String> mimeTypeHash = new HashMap<String, String>();

        mimeTypeHash.put("html", "text/html");
        mimeTypeHash.put("htm", "text/html");
        mimeTypeHash.put("gif", "image/gif");
        mimeTypeHash.put("jpg", "image/jpeg");
        mimeTypeHash.put("jpeg", "image/jpeg");
        mimeTypeHash.put("png", "image/png");
        mimeTypeHash.put("js", "text/javascript");
        mimeTypeHash.put("txt", "text/plain");
        mimeTypeHash.put("pdf", "application/pdf");
        mimeTypeHash.put("xls", "application/msexcel");
        mimeTypeHash.put("doc", "application/msword");
        mimeTypeHash.put("ppt", "application/mspowerpoint");
        mimeTypeHash.put("rtf", "text/rtf");

        // Source: http://framework.openoffice.org/documentation/mimetypes/mimetypes.html
        mimeTypeHash.put("sds", "application/vnd.stardivision.chart");
        mimeTypeHash.put("sdc", "application/vnd.stardivision.calc");
        mimeTypeHash.put("sdw", "application/vnd.stardivision.writer");
        mimeTypeHash.put("sgl", "application/vnd.stardivision.writer-global");
        mimeTypeHash.put("sda", "application/vnd.stardivision.draw");
        mimeTypeHash.put("sdd", "application/vnd.stardivision.impress");
        mimeTypeHash.put("sdf", "application/vnd.stardivision.math");
        mimeTypeHash.put("sxw", "application/vnd.sun.xml.writer");
        mimeTypeHash.put("stw", "application/vnd.sun.xml.writer.template");
        mimeTypeHash.put("sxg", "application/vnd.sun.xml.writer.global");
        mimeTypeHash.put("sxc", "application/vnd.sun.xml.calc");
        mimeTypeHash.put("stc", "application/vnd.sun.xml.calc.template");
        mimeTypeHash.put("sxi", "application/vnd.sun.xml.impress");
        mimeTypeHash.put("sti", "application/vnd.sun.xml.impress.template");
        mimeTypeHash.put("sxd", "application/vnd.sun.xml.draw");
        mimeTypeHash.put("std", "application/vnd.sun.xml.draw.template");
        mimeTypeHash.put("sxm", "application/vnd.sun.xml.math");
        mimeTypeHash.put("odt", "application/vnd.oasis.opendocument.text");
        mimeTypeHash.put("ott", "application/vnd.oasis.opendocument.text-template");
        mimeTypeHash.put("oth", "application/vnd.oasis.opendocument.text-web");
        mimeTypeHash.put("odm", "application/vnd.oasis.opendocument.text-master");
        mimeTypeHash.put("odg", "application/vnd.oasis.opendocument.graphics");
        mimeTypeHash.put("otg", "application/vnd.oasis.opendocument.graphics-template");
        mimeTypeHash.put("odp", "application/vnd.oasis.opendocument.presentation");
        mimeTypeHash.put("otp", "application/vnd.oasis.opendocument.presentation-template");
        mimeTypeHash.put("ods", "application/vnd.oasis.opendocument.spreadsheet");
        mimeTypeHash.put("ots", "application/vnd.oasis.opendocument.spreadsheet-template");
        mimeTypeHash.put("odc", "application/vnd.oasis.opendocument.chart");
        mimeTypeHash.put("odf", "application/vnd.oasis.opendocument.formula");
        mimeTypeHash.put("odb", "application/vnd.oasis.opendocument.database");
        mimeTypeHash.put("odi", "application/vnd.oasis.opendocument.image");

        // Source: http://blogs.technet.com/b/office_resource_kit/archive/2009/06/30/register-office-2007-file-format-mime-types-on-servers.aspx
        mimeTypeHash.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        mimeTypeHash.put("docm", "application/vnd.ms-word.document.macroEnabled.12");
        mimeTypeHash.put("dotx", "application/vnd.openxmlformats-officedocument.wordprocessingml.template");
        mimeTypeHash.put("dotm", "application/vnd.ms-word.template.macroEnabled.12");
        mimeTypeHash.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        mimeTypeHash.put("xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12");
        mimeTypeHash.put("xltx", "application/vnd.openxmlformats-officedocument.spreadsheetml.template");
        mimeTypeHash.put("xltm", "application/vnd.ms-excel.template.macroEnabled.12");
        mimeTypeHash.put("xlsb", "application/vnd.ms-excel.sheet.binary.macroEnabled.12");
        mimeTypeHash.put("xlam", "application/vnd.ms-excel.addin.macroEnabled.12");
        mimeTypeHash.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        mimeTypeHash.put("pptm", "application/vnd.ms-powerpoint.presentation.macroEnabled.12");
        mimeTypeHash.put("ppsx", "application/vnd.openxmlformats-officedocument.presentationml.slideshow");
        mimeTypeHash.put("ppsm", "application/vnd.ms-powerpoint.slideshow.macroEnabled.12");
        mimeTypeHash.put("potx", "application/vnd.openxmlformats-officedocument.presentationml.template");
        mimeTypeHash.put("potm", "application/vnd.ms-powerpoint.template.macroEnabled.12");
        mimeTypeHash.put("ppam", "application/vnd.ms-powerpoint.addin.macroEnabled.12");
        mimeTypeHash.put("sldx", "application/vnd.openxmlformats-officedocument.presentationml.slide");
        mimeTypeHash.put("sldm", "application/vnd.ms-powerpoint.slide.macroEnabled.12");
        mimeTypeHash.put("one", "application/onenote");
        mimeTypeHash.put("onetoc2", "application/onenote");
        mimeTypeHash.put("onetmp", "application/onenote");
        mimeTypeHash.put("onepkg", "application/onenote");
        mimeTypeHash.put("thmx", "application/vnd.ms-officetheme");

        mMimeTypeHash = mimeTypeHash;
      }

      // Set the MIME type
      String filename = file.getName();
      int lastDot = filename.lastIndexOf('.');
      if (lastDot != -1) {
        String extension = filename.substring(lastDot + 1);
        String mimeType = mMimeTypeHash.get(extension);
        if (mimeType != null) {
          response.setHeader("Content-Type", mimeType);
        }
      }

      // Send the file
      OutputStream out = null;
      FileInputStream in = null;
      try {
        out = response.getOutputStream();
        in = new FileInputStream(file);
        RegainToolkit.pipe(in, out);
      } catch (IOException exc) {
        throw new RegainException("Sending file failed: " + file.getAbsolutePath(), exc);
      } finally {
        if (in != null) {
          try {
            in.close();
          } catch (IOException exc) {
          }
        }
        if (out != null) {
          try {
            out.close();
          } catch (IOException exc) {
          }
        }
      }
    }
  }

  /**
   * Get the content of a compressed lucene field.
   *
   * @param doc	Lucene Index entry
   * @param fieldname	Index entry name
   * @return String that was in this field
   * @throws RegainException	If decompression failed.
   */
  public static String getCompressedFieldValue(Document doc, String fieldname) throws RegainException {
    byte[] compressedFieldValue = doc.getBinaryValue(fieldname);
    String value = "";
    if (compressedFieldValue != null) {
      try {
        value = CompressionTools.decompressString(compressedFieldValue);
      } catch (DataFormatException dataFormatException) {
        throw new RegainException("Couldn't uncompress field value.", dataFormatException);
      }
    }
    return value;
  }

  /**
   * Loads the configuration of the search mask.
   * <p>
   * If the configuration is already loaded, nothing is done.
   *
   * @param request The page request. Used to get the "configFile" init
   * parameter, which holds the name of the configuration file.
   * @throws RegainException If loading failed.
   */
  private static void loadConfiguration(PageRequest request)
          throws RegainException {
    if (mConfig == null) {
      // Create the factory
      String factoryClassname = request.getInitParameter("searchConfigFactoryClass");
      String factoryJarfile = request.getInitParameter("searchConfigFactoryJar");
      if (factoryClassname == null) {
        factoryClassname = DefaultSearchConfigFactory.class.getName();
      }
      SearchConfigFactory factory = (SearchConfigFactory) RegainToolkit.createClassInstance(factoryClassname, SearchConfigFactory.class, factoryJarfile);

      // Create the config
      mConfig = factory.createSearchConfig(request);
    }
  }
}
