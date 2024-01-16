package com.browseengine.bobo.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.lucene.util.PriorityQueue;

import com.browseengine.bobo.api.BrowseFacet;
import com.browseengine.bobo.api.BrowseRequest;
import com.browseengine.bobo.api.FacetAccessible;
import com.browseengine.bobo.api.FacetSpec;
import com.browseengine.bobo.api.FacetSpec.FacetSortSpec;
import com.browseengine.bobo.api.MappedFacetAccessible;
import com.browseengine.bobo.facets.impl.FacetHitcountComparatorFactory;

public class ListMerger {
  public static class MergedIterator<T> implements Iterator<T> {
    private class IteratorNode {
      public Iterator<T> _iterator;
      public T _curVal;

      public IteratorNode(Iterator<T> iterator) {
        _iterator = iterator;
        _curVal = null;
      }

      public boolean fetch() {
        if (_iterator.hasNext()) {
          _curVal = _iterator.next();
          return true;
        }
        _curVal = null;
        return false;
      }
    }

    @SuppressWarnings("rawtypes")
    private final PriorityQueue _queue;

    private MergedIterator(final int length, final Comparator<T> comparator) {
      _queue = new PriorityQueue<>(length) {
        @SuppressWarnings("unchecked")
        @Override
        protected boolean lessThan(Object o1, Object o2) {
          T v1 = ((IteratorNode) o1)._curVal;
          T v2 = ((IteratorNode) o2)._curVal;

          return (comparator.compare(v1, v2) < 0);
        }
      };
    }

    @SuppressWarnings("unchecked")
    public MergedIterator(final List<Iterator<T>> iterators, final Comparator<T> comparator) {
      this(iterators.size(), comparator);
      for (Iterator<T> iterator : iterators) {
        IteratorNode ctx = new IteratorNode(iterator);
        if (ctx.fetch()) _queue.add(ctx);
      }
    }

    @SuppressWarnings("unchecked")
    public MergedIterator(final Iterator<T>[] iterators, final Comparator<T> comparator) {
      this(iterators.length, comparator);
      for (Iterator<T> iterator : iterators) {
        IteratorNode ctx = new IteratorNode(iterator);
        if (ctx.fetch()) _queue.add(ctx);
      }
    }

    @Override
    public boolean hasNext() {
      return _queue.size() > 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T next() {
      IteratorNode ctx = (IteratorNode) _queue.top();
      T val = ctx._curVal;
      if (ctx.fetch()) {
        _queue.updateTop();
      } else {
        _queue.pop();
      }
      return val;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private ListMerger() {
  }

  public static <T> Iterator<T> mergeLists(final Iterator<T>[] iterators,
      final Comparator<T> comparator) {
    return new MergedIterator<>(iterators, comparator);
  }

  public static <T> Iterator<T> mergeLists(final List<Iterator<T>> iterators,
      final Comparator<T> comparator) {
    return new MergedIterator<>(iterators, comparator);
  }

  public static <T> ArrayList<T> mergeLists(int offset, int count, Iterator<T>[] iterators,
      Comparator<T> comparator) {
    return mergeLists(offset, count, new MergedIterator<>(iterators, comparator));
  }

  public static <T> ArrayList<T> mergeLists(int offset, int count, List<Iterator<T>> iterators,
      Comparator<T> comparator) {
    return mergeLists(offset, count, new MergedIterator<>(iterators, comparator));
  }

  private static <T> ArrayList<T> mergeLists(int offset, int count, Iterator<T> mergedIter) {
    if (count == 0) return new ArrayList<>();
    for (int c = 0; c < offset && mergedIter.hasNext(); c++) {
      mergedIter.next();
    }

    ArrayList<T> mergedList = new ArrayList<>();

    for (int c = 0; c < count && mergedIter.hasNext(); c++) {
      mergedList.add(mergedIter.next());
    }

    return mergedList;
  }

  public static final Comparator<BrowseFacet> FACET_VAL_COMPARATOR = new Comparator<>() {

    @Override
    public int compare(BrowseFacet o1, BrowseFacet o2) {
      int ret = o1.getValue().compareTo(o2.getValue());
      if (o1.getValue().startsWith("-") && o2.getValue().startsWith("-")) {
        ret = -ret;
      }
      return ret;
    }

  };

  public static Map<String, FacetAccessible> mergeSimpleFacetContainers(
      Collection<Map<String, FacetAccessible>> subMaps, BrowseRequest req) {
    Map<String, Map<String, BrowseFacet>> counts = new HashMap<>();
    for (Map<String, FacetAccessible> subMap : subMaps) {
      for (Map.Entry<String, FacetAccessible> entry : subMap.entrySet()) {
        Map<String, BrowseFacet> count = counts.get(entry.getKey());
        if (count == null) {
          count = new HashMap<>();
          counts.put(entry.getKey(), count);
        }
        for (BrowseFacet facet : entry.getValue().getFacets()) {
          String val = facet.getValue();
          BrowseFacet oldValue = count.get(val);
          if (oldValue == null) {
            count.put(val, new BrowseFacet(val, facet.getFacetValueHitCount()));
          } else {
            oldValue.setFacetValueHitCount(oldValue.getFacetValueHitCount()
                + facet.getFacetValueHitCount());
          }
        }
      }
    }

    Map<String, FacetAccessible> mergedFacetMap = new HashMap<>();
    for (String facet : counts.keySet()) {
      FacetSpec fs = req.getFacetSpec(facet);

      FacetSpec.FacetSortSpec sortSpec = fs.getOrderBy();

      Comparator<BrowseFacet> comparator;
      if (FacetSortSpec.OrderValueAsc.equals(sortSpec)) comparator = FACET_VAL_COMPARATOR;
      else if (FacetSortSpec.OrderHitsDesc.equals(sortSpec)) comparator = FacetHitcountComparatorFactory.FACET_HITS_COMPARATOR;
      else comparator = fs.getCustomComparatorFactory().newComparator();

      Map<String, BrowseFacet> facetValueCounts = counts.get(facet);
      BrowseFacet[] facetArray = facetValueCounts.values().toArray(
        new BrowseFacet[facetValueCounts.size()]);
      Arrays.sort(facetArray, comparator);

      int numToShow = facetArray.length;
      if (req != null) {
        FacetSpec fspec = req.getFacetSpec(facet);
        if (fspec != null) {
          int maxCount = fspec.getMaxCount();
          if (maxCount > 0) {
            numToShow = Math.min(maxCount, numToShow);
          }
        }
      }

      BrowseFacet[] facets;
      if (numToShow == facetArray.length) {
        facets = facetArray;
      } else {
        facets = new BrowseFacet[numToShow];
        System.arraycopy(facetArray, 0, facets, 0, numToShow);
      }

      MappedFacetAccessible mergedFacetAccessible = new MappedFacetAccessible(facets);
      mergedFacetMap.put(facet, mergedFacetAccessible);
    }
    return mergedFacetMap;
  }
}
