/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.metron.solr.integration.components;

import com.google.common.base.Function;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.metron.common.Constants;
import org.apache.metron.indexing.dao.metaalert.MetaAlertConstants;
import org.apache.metron.integration.InMemoryComponent;
import org.apache.metron.integration.UnableToStartException;
import org.apache.metron.solr.dao.SolrUtilities;
import org.apache.metron.solr.writer.MetronSolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.JettyConfig;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.cloud.MiniSolrCloudCluster;
import org.apache.solr.common.SolrDocument;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.solr.common.SolrInputDocument;
import org.apache.zookeeper.KeeperException;

public class SolrComponent implements InMemoryComponent {

  public static class Builder {

    private int port = 8983;
    private String solrXmlPath = "../metron-solr/src/test/resources/solr/solr.xml";
    private Map<String, String> initialCollections = new HashMap<>();
    private Function<SolrComponent, Void> postStartCallback;

    public Builder withPort(int port) {
      this.port = port;
      return this;
    }

    public Builder withSolrXmlPath(String solrXmlPath) {
      this.solrXmlPath = solrXmlPath;
      return this;
    }

    public Builder addInitialCollection(String name, String configPath) {
      initialCollections.put(name, configPath);
      return this;
    }

    public Builder withPostStartCallback(Function<SolrComponent, Void> f) {
      postStartCallback = f;
      return this;
    }

    public SolrComponent build() {
      return new SolrComponent(port, solrXmlPath, initialCollections, postStartCallback);
    }
  }

  private int port;
  private String solrXmlPath;
  private Map<String, String> collections;
  private MiniSolrCloudCluster miniSolrCloudCluster;
  private Function<SolrComponent, Void> postStartCallback;

  private SolrComponent(int port, String solrXmlPath, Map<String, String> collections,
      Function<SolrComponent, Void> postStartCallback) {
    this.port = port;
    this.solrXmlPath = solrXmlPath;
    this.collections = collections;
    this.postStartCallback = postStartCallback;
  }

  @Override
  public void start() throws UnableToStartException {
    try {
      File baseDir = Files.createTempDirectory("solrcomponent").toFile();
      baseDir.deleteOnExit();
      miniSolrCloudCluster = new MiniSolrCloudCluster(1, baseDir.toPath(),
          JettyConfig.builder().setPort(port).build());
      for(String name: collections.keySet()) {
        String configPath = collections.get(name);
        miniSolrCloudCluster.uploadConfigSet(new File(configPath).toPath(), name);
        CollectionAdminRequest.createCollection(name, 1, 1).process(miniSolrCloudCluster.getSolrClient());
      }
      if (postStartCallback != null) {
        postStartCallback.apply(this);
      }
    } catch (Exception e) {
      throw new UnableToStartException(e.getMessage(), e);
    }
  }

  @Override
  public void stop() {
    try {
      miniSolrCloudCluster.deleteAllCollections();
      miniSolrCloudCluster.shutdown();
    } catch (Exception e) {
      // Do nothing
    }
  }

  @Override
  public void reset() {
    try {
      miniSolrCloudCluster.deleteAllCollections();
    } catch (Exception e) {
      // Do nothing
    }
  }

  public MetronSolrClient getSolrClient() {
    return new MetronSolrClient(getZookeeperUrl());
  }

  public MiniSolrCloudCluster getMiniSolrCloudCluster() {
    return this.miniSolrCloudCluster;
  }

  public String getZookeeperUrl() {
    return miniSolrCloudCluster.getZkServer().getZkAddress();
  }

  public void addCollection(String name, String configPath)
      throws InterruptedException, IOException, KeeperException, SolrServerException {
    miniSolrCloudCluster.uploadConfigSet(new File(configPath).toPath(), name);
    CollectionAdminRequest.createCollection(name, 1, 1)
        .process(miniSolrCloudCluster.getSolrClient());
  }

  public boolean hasCollection(String collection) {
    MetronSolrClient solr = getSolrClient();
    boolean collectionFound = false;
    try {
      collectionFound = solr.listCollections().contains(collection);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return collectionFound;
  }

  public List<Map<String, Object>> getAllIndexedDocs(String collection) {
    List<Map<String, Object>> docs = new ArrayList<>();
    CloudSolrClient solr = miniSolrCloudCluster.getSolrClient();
    solr.setDefaultCollection(collection);
    SolrQuery parameters = new SolrQuery();

    // If it's metaalert, we need to adjust the query. We want child docs with the parent,
    // not separate.
    if (collection.equals("metaalert")) {
      parameters.setQuery("source.type:metaalert")
          .setFields("*", "[child parentFilter=source.type:metaalert limit=999]");
    } else {
      parameters.set("q", "*:*");
    }
    try {
      solr.commit();
      QueryResponse response = solr.query(parameters);
      for (SolrDocument solrDocument : response.getResults()) {
        // Use the utils to make sure we get child docs.
        docs.add(SolrUtilities.toDocument(solrDocument).getDocument());
      }
    } catch (SolrServerException | IOException e) {
      e.printStackTrace();
    }
    return docs;
  }

  public void addDocs(String collection, List<Map<String, Object>> docs)
      throws IOException, SolrServerException {
    CloudSolrClient solr = miniSolrCloudCluster.getSolrClient();
    solr.setDefaultCollection(collection);
    Collection<SolrInputDocument> solrInputDocuments = docs.stream().map(doc -> {
      SolrInputDocument solrInputDocument = new SolrInputDocument();
      for (Entry<String, Object> entry : doc.entrySet()) {
        // If the entry itself is a map, add it as a child document. Handle one level of nesting.
        if (entry.getValue() instanceof List && !entry.getKey().equals(
            MetaAlertConstants.METAALERT_FIELD)) {
          for (Object entryItem : (List)entry.getValue()) {
            if (entryItem instanceof Map) {
              @SuppressWarnings("unchecked")
              Map<String, Object> childDoc = (Map<String, Object>) entryItem;
              SolrInputDocument childInputDoc = new SolrInputDocument();
              for (Entry<String, Object> childEntry : childDoc.entrySet()) {
                childInputDoc.addField(childEntry.getKey(), childEntry.getValue());
              }
              solrInputDocument.addChildDocument(childInputDoc);
            }
          }
        } else {
          solrInputDocument.addField(entry.getKey(), entry.getValue());
        }
      }
      return solrInputDocument;
    }).collect(Collectors.toList());

    checkUpdateResponse(solr.add(collection, solrInputDocuments));
    // Make sure to commit so things show up
    checkUpdateResponse(solr.commit(true, true));
  }

  protected void checkUpdateResponse(UpdateResponse result) throws IOException {
    if (result.getStatus() != 0) {
      throw new IOException("Response error received while adding documents: " + result);
    }
  }
}
