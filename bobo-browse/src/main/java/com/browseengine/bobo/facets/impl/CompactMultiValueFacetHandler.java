package com.browseengine.bobo.facets.impl;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.util.BytesRef;

import com.browseengine.bobo.api.BoboSegmentReader;
import com.browseengine.bobo.api.BrowseFacet;
import com.browseengine.bobo.api.BrowseSelection;
import com.browseengine.bobo.api.FacetIterator;
import com.browseengine.bobo.api.FacetSpec;
import com.browseengine.bobo.facets.FacetCountCollector;
import com.browseengine.bobo.facets.FacetCountCollectorSource;
import com.browseengine.bobo.facets.FacetHandler;
import com.browseengine.bobo.facets.data.FacetDataCache;
import com.browseengine.bobo.facets.data.TermListFactory;
import com.browseengine.bobo.facets.data.TermStringList;
import com.browseengine.bobo.facets.data.TermValueList;
import com.browseengine.bobo.facets.filter.CompactMultiValueFacetFilter;
import com.browseengine.bobo.facets.filter.EmptyFilter;
import com.browseengine.bobo.facets.filter.RandomAccessAndFilter;
import com.browseengine.bobo.facets.filter.RandomAccessFilter;
import com.browseengine.bobo.facets.filter.RandomAccessNotFilter;
import com.browseengine.bobo.query.scoring.BoboDocScorer;
import com.browseengine.bobo.query.scoring.FacetScoreable;
import com.browseengine.bobo.query.scoring.FacetTermScoringFunctionFactory;
import com.browseengine.bobo.sort.DocComparator;
import com.browseengine.bobo.sort.DocComparatorSource;
import com.browseengine.bobo.util.BigIntArray;
import com.browseengine.bobo.util.BigSegmentedArray;
import com.browseengine.bobo.util.StringArrayComparator;

public class CompactMultiValueFacetHandler extends FacetHandler<FacetDataCache<?>> implements
    FacetScoreable {
  private static final int MAX_VAL_COUNT = 32;
  private final TermListFactory<?> _termListFactory;
  private final String _indexFieldName;

  public CompactMultiValueFacetHandler(String name, String indexFieldName,
      TermListFactory<?> termListFactory) {
    super(name);
    _indexFieldName = indexFieldName;
    _termListFactory = termListFactory;
  }

  public CompactMultiValueFacetHandler(String name, TermListFactory<?> termListFactory) {
    this(name, name, termListFactory);
  }

  public CompactMultiValueFacetHandler(String name, String indexFieldName) {
    this(name, indexFieldName, null);
  }

  public CompactMultiValueFacetHandler(String name) {
    this(name, name, null);
  }

  @Override
  public DocComparatorSource getDocComparatorSource() {
    return new CompactMultiFacetDocComparatorSource(this);
  }

  @Override
  public RandomAccessFilter buildRandomAccessFilter(String value, Properties prop)
      throws IOException {
    return new CompactMultiValueFacetFilter(this, value);
  }

  @Override
  public RandomAccessFilter buildRandomAccessAndFilter(String[] vals, Properties prop)
      throws IOException {
    ArrayList<RandomAccessFilter> filterList = new ArrayList<>(vals.length);

    for (String val : vals) {
      RandomAccessFilter f = buildRandomAccessFilter(val, prop);
      if (f != null) {
        filterList.add(f);
      } else {
        return EmptyFilter.getInstance();
      }
    }
    if (filterList.size() == 1) return filterList.get(0);
    return new RandomAccessAndFilter(filterList);
  }

  @Override
  public RandomAccessFilter buildRandomAccessOrFilter(String[] vals, Properties prop, boolean isNot)
      throws IOException {
    RandomAccessFilter filter = null;

    if (vals.length > 0) {
      filter = new CompactMultiValueFacetFilter(this, vals);
    } else {
      filter = EmptyFilter.getInstance();
    }
    if (isNot) {
      filter = new RandomAccessNotFilter(filter);
    }
    return filter;
  }

  private static int countBits(int val) {
    int c = 0;
    for (c = 0; val > 0; c++) {
      val &= val - 1;
    }
    return c;
  }

  @Override
  public int getNumItems(BoboSegmentReader reader, int id) {
    FacetDataCache<?> dataCache = getFacetData(reader);
    if (dataCache == null) return 0;
    int encoded = dataCache.orderArray.get(id);
    return countBits(encoded);
  }

  @Override
  public String[] getFieldValues(BoboSegmentReader reader, int id) {
    FacetDataCache<?> dataCache = getFacetData(reader);
    if (dataCache == null) return new String[0];
    int encoded = dataCache.orderArray.get(id);
    if (encoded == 0) {
      return new String[] { "" };
    } else {
      int count = 1;
      ArrayList<String> valList = new ArrayList<>(MAX_VAL_COUNT);

      while (encoded != 0) {
        if ((encoded & 0x00000001) != 0x0) {
          valList.add(dataCache.valArray.get(count));
        }
        count++;
        encoded >>>= 1;
      }
      return valList.toArray(new String[valList.size()]);
    }
  }

  @Override
  public Object[] getRawFieldValues(BoboSegmentReader reader, int id) {
    FacetDataCache<?> dataCache = getFacetData(reader);
    if (dataCache == null) return new String[0];
    int encoded = dataCache.orderArray.get(id);
    if (encoded == 0) {
      return new Object[0];
    } else {
      int count = 1;
      ArrayList<Object> valList = new ArrayList<>(MAX_VAL_COUNT);

      while (encoded != 0) {
        if ((encoded & 0x00000001) != 0x0) {
          valList.add(dataCache.valArray.getRawValue(count));
        }
        count++;
        encoded >>>= 1;
      }
      return valList.toArray(new Object[valList.size()]);
    }
  }

  @Override
  public FacetCountCollectorSource getFacetCountCollectorSource(final BrowseSelection sel,
      final FacetSpec ospec) {
    return new FacetCountCollectorSource() {

      @Override
      public FacetCountCollector getFacetCountCollector(BoboSegmentReader reader, int docBase) {
        final FacetDataCache<?> dataCache = CompactMultiValueFacetHandler.this.getFacetData(reader);
        return new CompactMultiValueFacetCountCollector(_name, sel, dataCache, docBase, ospec);
      }
    };
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Override
  public FacetDataCache<?> load(BoboSegmentReader reader) throws IOException {
    int maxDoc = reader.maxDoc();

    BigIntArray order = new BigIntArray(maxDoc);

    TermValueList<?> mterms = _termListFactory == null ? new TermStringList() : _termListFactory
        .createTermList();

    IntArrayList minIDList = new IntArrayList();
    IntArrayList maxIDList = new IntArrayList();
    IntArrayList freqList = new IntArrayList();

    int t = 0; // current term number
    mterms.add(null);
    minIDList.add(-1);
    maxIDList.add(-1);
    freqList.add(0);
    t++;
    Terms terms = reader.terms(_indexFieldName);
    if (terms != null) {
      TermsEnum termsEnum = terms.iterator(null);
      BytesRef text;
      while ((text = termsEnum.next()) != null) {
        // store term text
        // we expect that there is at most one term per document
        if (t > MAX_VAL_COUNT) {
          throw new IOException("maximum number of value cannot exceed: " + MAX_VAL_COUNT);
        }
        String val = text.utf8ToString();
        mterms.add(val);
        int bit = (0x00000001 << (t - 1));
        Term term = new Term(_indexFieldName, val);
        DocsEnum docsEnum = reader.termDocsEnum(term);
        // freqList.add(termEnum.docFreq()); // removed because the df doesn't take into account the
        // num of deletedDocs
        int df = 0;
        int minID = -1;
        int maxID = -1;
        int docID = -1;
        while ((docID = docsEnum.nextDoc()) != DocsEnum.NO_MORE_DOCS) {
          df++;
          order.add(docID, order.get(docID) | bit);
          minID = docID;
          while (docsEnum.nextDoc() != DocsEnum.NO_MORE_DOCS) {
            docID = docsEnum.docID();
            df++;
            order.add(docID, order.get(docID) | bit);
          }
          maxID = docID;
        }
        freqList.add(df);
        minIDList.add(minID);
        maxIDList.add(maxID);
        t++;
      }
    }

    mterms.seal();

    return new FacetDataCache(order, mterms, freqList.toIntArray(), minIDList.toIntArray(),
        maxIDList.toIntArray(), TermCountSize.large);
  }

  private static class CompactMultiFacetDocComparatorSource extends DocComparatorSource {
    private final CompactMultiValueFacetHandler _facetHandler;

    private CompactMultiFacetDocComparatorSource(CompactMultiValueFacetHandler facetHandler) {
      _facetHandler = facetHandler;
    }

    @Override
    public DocComparator getComparator(final AtomicReader reader, int docbase) throws IOException {
      if (!(reader instanceof BoboSegmentReader)) throw new IllegalStateException(
          "reader must be instance of " + BoboSegmentReader.class);
      final FacetDataCache<?> dataCache = _facetHandler.getFacetData((BoboSegmentReader) reader);
      return new DocComparator() {
        @Override
        public int compare(ScoreDoc doc1, ScoreDoc doc2) {
          int encoded1 = dataCache.orderArray.get(doc1.doc);
          int encoded2 = dataCache.orderArray.get(doc2.doc);
          return encoded1 - encoded2;
        }

        @Override
        public Comparable<?> value(ScoreDoc doc) {
          return new StringArrayComparator(_facetHandler.getFieldValues((BoboSegmentReader) reader,
            doc.doc));
        }
      };
    }
  }

  @Override
  public BoboDocScorer getDocScorer(BoboSegmentReader reader,
      FacetTermScoringFunctionFactory scoringFunctionFactory, Map<String, Float> boostMap) {
    FacetDataCache<?> dataCache = getFacetData(reader);
    float[] boostList = BoboDocScorer.buildBoostList(dataCache.valArray, boostMap);
    return new CompactMultiValueDocScorer(dataCache, scoringFunctionFactory, boostList);
  }

  private static final class CompactMultiValueDocScorer extends BoboDocScorer {
    private final FacetDataCache<?> _dataCache;

    CompactMultiValueDocScorer(FacetDataCache<?> dataCache,
        FacetTermScoringFunctionFactory scoreFunctionFactory, float[] boostList) {
      super(scoreFunctionFactory.getFacetTermScoringFunction(dataCache.valArray.size(),
        dataCache.orderArray.size()), boostList);
      _dataCache = dataCache;
    }

    @Override
    public Explanation explain(int doc) {
      int encoded = _dataCache.orderArray.get(doc);

      int count = 1;
      FloatList scoreList = new FloatArrayList(_dataCache.valArray.size());
      ArrayList<Explanation> explList = new ArrayList<>(scoreList.size());
      while (encoded != 0) {
        if ((encoded & 0x00000001) != 0x0) {
          int idx = count - 1;
          scoreList.add(_function.score(_dataCache.freqs[idx], _boostList[idx]));
          explList.add(_function.explain(_dataCache.freqs[idx], _boostList[idx]));
        }
        count++;
        encoded >>>= 1;
      }
      Explanation topLevel = _function.explain(scoreList.toFloatArray());
      for (Explanation sub : explList) {
        topLevel.addDetail(sub);
      }
      return topLevel;
    }

    @Override
    public final float score(int docid) {
      _function.clearScores();
      int encoded = _dataCache.orderArray.get(docid);

      int count = 1;

      while (encoded != 0) {
        int idx = count - 1;
        if ((encoded & 0x00000001) != 0x0) {
          _function.scoreAndCollect(_dataCache.freqs[idx], _boostList[idx]);
        }
        count++;
        encoded >>>= 1;
      }
      return _function.getCurrentScore();
    }

  }

  private static final class CompactMultiValueFacetCountCollector extends
      DefaultFacetCountCollector {
    private final BigSegmentedArray _array;
    private final int[] _combinationCount = new int[16 * 8];
    private int _noValCount = 0;
    private boolean _aggregated = false;

    CompactMultiValueFacetCountCollector(String name, BrowseSelection sel,
        FacetDataCache<?> dataCache, int docBase, FacetSpec ospec) {
      super(name, dataCache, docBase, sel, ospec);
      _array = _dataCache.orderArray;
    }

    @Override
    public final void collectAll() {
      _count = BigIntArray.fromArray(_dataCache.freqs);
      _aggregated = true;
    }

    @Override
    public final void collect(int docid) {
      int encoded = _array.get(docid);
      if (encoded == 0) {
        _noValCount++;
      } else {
        int offset = 0;
        while (true) {
          _combinationCount[(encoded & 0x0F) + offset]++;
          encoded = (encoded >>> 4);
          if (encoded == 0) break;
          offset += 16;
        }
      }
    }

    @Override
    public BrowseFacet getFacet(String value) {
      if (!_aggregated) aggregateCounts();
      return super.getFacet(value);
    }

    @Override
    public int getFacetHitsCount(Object value) {
      if (!_aggregated) aggregateCounts();
      return super.getFacetHitsCount(value);
    }

    @Override
    public BigSegmentedArray getCountDistribution() {
      if (!_aggregated) aggregateCounts();
      return _count;
    }

    @Override
    public List<BrowseFacet> getFacets() {
      if (!_aggregated) aggregateCounts();
      return super.getFacets();
    }

    private void aggregateCounts() {
      _count.add(0, _noValCount);

      for (int i = 1; i < _combinationCount.length; i++) {
        int count = _combinationCount[i];
        if (count > 0) {
          int offset = (i >> 4) * 4;
          int encoded = (i & 0x0F);
          int index = 1;
          while (encoded != 0) {
            if ((encoded & 0x00000001) != 0x0) {
              int idx = index + offset;
              _count.add(idx, _count.get(idx) + count);
            }
            index++;
            encoded >>>= 1;
          }
        }
      }
      _aggregated = true;
    }

    @Override
    public FacetIterator iterator() {
      if (!_aggregated) aggregateCounts();
      return super.iterator();
    }
  }
}
