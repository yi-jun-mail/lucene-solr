package org.apache.lucene.search.spans;

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

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.Bits;

import java.io.IOException;
import java.util.ArrayList;

/** Keep matches that are contained within another Spans. */
public class SpanWithinQuery extends SpanContainQuery {

  /** Construct a SpanWithinQuery matching spans from <code>little</code>
   * that are inside of <code>big</code>.
   * This query has the boost of <code>little</code>.
   * <code>big</code> and <code>little</code> must be in the same field.
   */
  public SpanWithinQuery(SpanQuery big, SpanQuery little) {
    super(big, little, little.getBoost());
  }

  @Override
  public String toString(String field) {
    return toString(field, "SpanWithin");
  }

  @Override
  public SpanWithinQuery clone() {
    return new SpanWithinQuery(
          (SpanQuery) big.clone(),
          (SpanQuery) little.clone());
  }

  @Override
  public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores, SpanCollectorFactory factory) throws IOException {
    SpanWeight bigWeight = big.createWeight(searcher, false, factory);
    SpanWeight littleWeight = little.createWeight(searcher, false, factory);
    SpanSimilarity similarity = SpanSimilarity.build(this, searcher, needsScores, bigWeight, littleWeight);
    return new SpanWithinWeight(similarity, factory, bigWeight, littleWeight);
  }

  public class SpanWithinWeight extends SpanContainWeight {

    public SpanWithinWeight(SpanSimilarity similarity, SpanCollectorFactory factory,
                            SpanWeight bigWeight, SpanWeight littleWeight) throws IOException {
      super(similarity, factory, bigWeight, littleWeight);
    }

    /**
     * Return spans from <code>little</code> that are contained in a spans from <code>big</code>.
     * The payload is from the spans of <code>little</code>.
     */
    @Override
    public Spans getSpans(final LeafReaderContext context, final Bits acceptDocs, SpanCollector collector) throws IOException {
      ArrayList<Spans> containerContained = prepareConjunction(context, acceptDocs, collector);
      if (containerContained == null) {
        return null;
      }

      Spans big = containerContained.get(0);
      Spans little = containerContained.get(1);

      return new ContainSpans(big, little, little) {

        @Override
        boolean twoPhaseCurrentDocMatches() throws IOException {
          oneExhaustedInCurrentDoc = false;
          assert littleSpans.startPosition() == -1;
          while (littleSpans.nextStartPosition() != NO_MORE_POSITIONS) {
            while (bigSpans.endPosition() < littleSpans.endPosition()) {
              if (bigSpans.nextStartPosition() == NO_MORE_POSITIONS) {
                oneExhaustedInCurrentDoc = true;
                return false;
              }
            }
            if (bigSpans.startPosition() <= littleSpans.startPosition()) {
              atFirstInCurrentDoc = true;
              return true;
            }
          }
          oneExhaustedInCurrentDoc = true;
          return false;
        }

        @Override
        public int nextStartPosition() throws IOException {
          if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            return littleSpans.startPosition();
          }
          while (littleSpans.nextStartPosition() != NO_MORE_POSITIONS) {
            while (bigSpans.endPosition() < littleSpans.endPosition()) {
              if (bigSpans.nextStartPosition() == NO_MORE_POSITIONS) {
                oneExhaustedInCurrentDoc = true;
                return NO_MORE_POSITIONS;
              }
            }
            if (bigSpans.startPosition() <= littleSpans.startPosition()) {
              return littleSpans.startPosition();
            }
          }
          oneExhaustedInCurrentDoc = true;
          return NO_MORE_POSITIONS;
        }
      };
    }
  }

}