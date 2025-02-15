package com.browseengine.bobo.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.util.PriorityQueue;

public class SearchResultMerger<T> {
  private SearchResultMerger() {

  }

  public static class MergedIterator<T> implements Iterator<T> {
    private class IteratorCtx {
      public Iterator<T> _iterator;
      public T _curVal;

      public IteratorCtx(Iterator<T> iterator) {
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

    @SuppressWarnings("unchecked")
    public MergedIterator(final List<Iterator<T>> sources, final Comparator<T> comparator) {
      _queue = new PriorityQueue<>(sources.size()) {
        @Override
        protected boolean lessThan(Object o1, Object o2) {
          T v1 = ((IteratorCtx) o1)._curVal;
          T v2 = ((IteratorCtx) o2)._curVal;

          return (comparator.compare(v1, v2) < 0);
        }
      };

      for (Iterator<T> iterator : sources) {
        IteratorCtx ctx = new IteratorCtx(iterator);
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
      IteratorCtx ctx = (IteratorCtx) _queue.top();
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

  public static <T> Iterator<T> mergeIterator(List<Iterator<T>> results, Comparator<T> comparator) {
    return new MergedIterator<>(results, comparator);
  }

  public static <T> ArrayList<T> mergeResult(int offset, int count, List<Iterator<T>> results,
      Comparator<T> comparator) {
    Iterator<T> mergedIter = mergeIterator(results, comparator);

    for (int c = 0; c < offset && mergedIter.hasNext(); c++) {
      mergedIter.next();
    }

    ArrayList<T> mergedList = new ArrayList<>();

    for (int c = 0; c < count && mergedIter.hasNext(); c++) {
      mergedList.add(mergedIter.next());
    }

    return mergedList;
  }
}
