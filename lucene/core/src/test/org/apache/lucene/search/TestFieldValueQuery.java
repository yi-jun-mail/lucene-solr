package org.apache.lucene.search;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

public class TestFieldValueQuery extends LuceneTestCase {

  public void testRandom() throws IOException {
    final int iters = atLeast(10);
    for (int iter = 0; iter < iters; ++iter) {
      Directory dir = newDirectory();
      RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
      final int numDocs = atLeast(100);
      for (int i = 0; i < numDocs; ++i) {
        Document doc = new Document();
        final boolean hasValue = random().nextBoolean();
        if (hasValue) {
          doc.add(new NumericDocValuesField("dv1", 1));
          doc.add(new SortedNumericDocValuesField("dv2", 1));
          doc.add(new SortedNumericDocValuesField("dv2", 2));
          doc.add(new StringField("has_value", "yes", Store.NO));
        }
        doc.add(new StringField("f", random().nextBoolean() ? "yes" : "no", Store.NO));
        iw.addDocument(doc);
      }
      if (random().nextBoolean()) {
        iw.deleteDocuments(new TermQuery(new Term("f", "no")));
      }
      iw.commit();
      final IndexReader reader = iw.getReader();
      final IndexSearcher searcher = newSearcher(reader);
      iw.close();

      assertSameMatches(searcher, new TermQuery(new Term("has_value", "yes")), new FieldValueQuery("dv1"), false);
      assertSameMatches(searcher, new TermQuery(new Term("has_value", "yes")), new FieldValueQuery("dv2"), false);

      reader.close();
      dir.close();
    }
  }

  public void testApproximation() throws IOException {
    final int iters = atLeast(10);
    for (int iter = 0; iter < iters; ++iter) {
      Directory dir = newDirectory();
      RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
      final int numDocs = atLeast(100);
      for (int i = 0; i < numDocs; ++i) {
        Document doc = new Document();
        final boolean hasValue = random().nextBoolean();
        if (hasValue) {
          doc.add(new NumericDocValuesField("dv1", 1));
          doc.add(new SortedNumericDocValuesField("dv2", 1));
          doc.add(new SortedNumericDocValuesField("dv2", 2));
          doc.add(new StringField("has_value", "yes", Store.NO));
        }
        doc.add(new StringField("f", random().nextBoolean() ? "yes" : "no", Store.NO));
        iw.addDocument(doc);
      }
      if (random().nextBoolean()) {
        iw.deleteDocuments(new TermQuery(new Term("f", "no")));
      }
      iw.commit();
      final IndexReader reader = iw.getReader();
      final IndexSearcher searcher = newSearcher(reader);
      iw.close();

      BooleanQuery ref = new BooleanQuery();
      ref.add(new TermQuery(new Term("f", "yes")), Occur.MUST);
      ref.add(new TermQuery(new Term("has_value", "yes")), Occur.FILTER);

      BooleanQuery bq1 = new BooleanQuery();
      bq1.add(new TermQuery(new Term("f", "yes")), Occur.MUST);
      bq1.add(new FieldValueQuery("dv1"), Occur.FILTER);
      assertSameMatches(searcher, ref, bq1, true);

      BooleanQuery bq2 = new BooleanQuery();
      bq2.add(new TermQuery(new Term("f", "yes")), Occur.MUST);
      bq2.add(new FieldValueQuery("dv2"), Occur.FILTER);
      assertSameMatches(searcher, ref, bq2, true);

      reader.close();
      dir.close();
    }
  }

  public void testScore() throws IOException {
    final int iters = atLeast(10);
    for (int iter = 0; iter < iters; ++iter) {
      Directory dir = newDirectory();
      RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
      final int numDocs = atLeast(100);
      for (int i = 0; i < numDocs; ++i) {
        Document doc = new Document();
        final boolean hasValue = random().nextBoolean();
        if (hasValue) {
          doc.add(new NumericDocValuesField("dv1", 1));
          doc.add(new SortedNumericDocValuesField("dv2", 1));
          doc.add(new SortedNumericDocValuesField("dv2", 2));
          doc.add(new StringField("has_value", "yes", Store.NO));
        }
        doc.add(new StringField("f", random().nextBoolean() ? "yes" : "no", Store.NO));
        iw.addDocument(doc);
      }
      if (random().nextBoolean()) {
        iw.deleteDocuments(new TermQuery(new Term("f", "no")));
      }
      iw.commit();
      final IndexReader reader = iw.getReader();
      final IndexSearcher searcher = newSearcher(reader);
      iw.close();

      final float boost = random().nextFloat() * 10;
      final Query ref = new ConstantScoreQuery(new TermQuery(new Term("has_value", "yes")));
      ref.setBoost(boost);

      final Query q1 = new FieldValueQuery("dv1");
      q1.setBoost(boost);
      assertSameMatches(searcher, ref, q1, true);

      final Query q2 = new FieldValueQuery("dv2");
      q2.setBoost(boost);
      assertSameMatches(searcher, ref, q2, true);

      reader.close();
      dir.close();
    }
  }

  public void testMissingField() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
    iw.addDocument(new Document());
    iw.commit();
    final IndexReader reader = iw.getReader();
    final IndexSearcher searcher = newSearcher(reader);
    iw.close();
    assertEquals(0, searcher.search(new FieldValueQuery("f"), 1).totalHits);
    reader.close();
    dir.close();
  }

  public void testAllDocsHaveField() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(new NumericDocValuesField("f", 1));
    iw.addDocument(doc);
    iw.commit();
    final IndexReader reader = iw.getReader();
    final IndexSearcher searcher = newSearcher(reader);
    iw.close();
    assertEquals(1, searcher.search(new FieldValueQuery("f"), 1).totalHits);
    reader.close();
    dir.close();
  }

  public void testFieldExistsButNoDocsHaveField() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
    // 1st segment has the field, but 2nd one does not
    Document doc = new Document();
    doc.add(new NumericDocValuesField("f", 1));
    iw.addDocument(doc);
    iw.commit();
    iw.addDocument(new Document());
    iw.commit();
    final IndexReader reader = iw.getReader();
    final IndexSearcher searcher = newSearcher(reader);
    iw.close();
    assertEquals(1, searcher.search(new FieldValueQuery("f"), 1).totalHits);
    reader.close();
    dir.close();
  }

  private void assertSameMatches(IndexSearcher searcher, Query q1, Query q2, boolean scores) throws IOException {
    final int maxDoc = searcher.getIndexReader().maxDoc();
    final TopDocs td1 = searcher.search(q1, maxDoc, scores ? Sort.RELEVANCE : Sort.INDEXORDER);
    final TopDocs td2 = searcher.search(q2, maxDoc, scores ? Sort.RELEVANCE : Sort.INDEXORDER);
    assertEquals(td1.totalHits, td2.totalHits);
    for (int i = 0; i < td1.scoreDocs.length; ++i) {
      assertEquals(td1.scoreDocs[i].doc, td2.scoreDocs[i].doc);
      if (scores) {
        assertEquals(td1.scoreDocs[i].score, td2.scoreDocs[i].score, 10e-7);
      }
    }
  }
}
