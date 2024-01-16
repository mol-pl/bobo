package com.browseengine.bobo.facets.attribute;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.browseengine.bobo.api.BrowseFacet;
import com.browseengine.bobo.api.BrowseSelection;
import com.browseengine.bobo.api.FacetIterator;
import com.browseengine.bobo.api.FacetSpec;
import com.browseengine.bobo.facets.data.MultiValueFacetDataCache;
import com.browseengine.bobo.facets.impl.DefaultFacetCountCollector;
import com.browseengine.bobo.util.BigIntArray;
import com.browseengine.bobo.util.BigNestedIntArray;

public final class AttributesFacetCountCollector extends DefaultFacetCountCollector {
  public final BigNestedIntArray _array;
  private List<BrowseFacet> cachedFacets;
  private final int numFacetsPerKey;
  private final char separator;
  private final MultiValueFacetDataCache<?> dataCache;
  private String[] values;

  @SuppressWarnings("rawtypes")
  public AttributesFacetCountCollector(AttributesFacetHandler attributesFacetHandler, String name,
      MultiValueFacetDataCache dataCache, int docBase, BrowseSelection browseSelection,
      FacetSpec ospec, int numFacetsPerKey, char separator) {
    super(name, dataCache, docBase, browseSelection, ospec);
    this.dataCache = dataCache;
    this.numFacetsPerKey = numFacetsPerKey;
    this.separator = separator;
    _array = dataCache._nestedArray;
    if (browseSelection != null) {
      values = browseSelection.getValues();
    }
  }

  @Override
  public final void collect(int docid) {
    dataCache._nestedArray.countNoReturn(docid, _count);
  }

  @Override
  public final void collectAll() {
    _count = BigIntArray.fromArray(_dataCache.freqs);
  }

  @Override
  public List<BrowseFacet> getFacets() {
    if (cachedFacets == null) {
      int max = _ospec.getMaxCount();
      _ospec.setMaxCount(max * 10);
      List<BrowseFacet> facets = super.getFacets();
      _ospec.setMaxCount(max);
      filterByKeys(facets, separator, numFacetsPerKey, values);
      cachedFacets = facets;
    }
    return cachedFacets;
  }

  private void filterByKeys(List<BrowseFacet> facets, char separator, int numFacetsPerKey,
      String[] values) {
    Map<String, AtomicInteger> keyOccurences = new HashMap<>();
    Iterator<BrowseFacet> iterator = facets.iterator();
    String separatorString = String.valueOf(separator);
    while (iterator.hasNext()) {
      BrowseFacet facet = iterator.next();
      String value = facet.getValue();
      if (!value.contains(separatorString)) {
        iterator.remove();
        continue;
      }
      if (values != null && values.length > 0) {
        boolean belongsToKeys = false;
        for (String val : values) {
          if (value.startsWith(val)) {
            belongsToKeys = true;
            break;
          }
        }
        if (!belongsToKeys) {
          iterator.remove();
          continue;
        }
      }
      String key = value.substring(0, value.indexOf(separatorString));
      AtomicInteger numOfKeys = keyOccurences.get(key);
      if (numOfKeys == null) {
        numOfKeys = new AtomicInteger(0);
        keyOccurences.put(key, numOfKeys);
      }
      int count = numOfKeys.incrementAndGet();
      if (count > numFacetsPerKey) {
        iterator.remove();
      }
    }
  }

  @Override
  public FacetIterator iterator() {
    return new AttributesFacetIterator(getFacets());
  }
}