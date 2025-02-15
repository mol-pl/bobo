package com.browseengine.bobo.docidset;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;

public class AndDocIdSet extends ImmutableDocSet implements Serializable {
  private static final long serialVersionUID = 1L;
  private final ArrayList<Integer> _interSectionResult = new ArrayList<>();

  public class DescDocIdSetComparator implements Comparator<StatefulDSIterator>, Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public int compare(StatefulDSIterator o1, StatefulDSIterator o2) {
      return o2.docID() - o1.docID();
    }

  }

  private List<DocIdSet> sets = null;
  private final int nonNullSize; // excludes nulls

  public AndDocIdSet(List<DocIdSet> docSets) {
    this.sets = docSets;
    int size = 0;
    if (sets != null) {
      for (DocIdSet set : sets) {
        if (set != null) size++;
      }
    }
    nonNullSize = size;
  }

  public ArrayList<Integer> getIntersection() {
    return _interSectionResult;
  }

  class AndDocIdSetIterator extends DocIdSetIterator {
    int lastReturn = -1;
    private DocIdSetIterator[] iterators = null;

    AndDocIdSetIterator() throws IOException {
      if (nonNullSize < 1) throw new IllegalArgumentException("Minimum one iterator required");

      iterators = new DocIdSetIterator[nonNullSize];
      int j = 0;
      for (DocIdSet set : sets) {
        if (set != null) {
          DocIdSetIterator dcit = set.iterator();
          if (dcit == null) dcit = DocIdSet.EMPTY_DOCIDSET.iterator();
          iterators[j++] = dcit;
        }
      }
      lastReturn = (iterators.length > 0 ? -1 : DocIdSetIterator.NO_MORE_DOCS);
    }

    @Override
    public final int docID() {
      return lastReturn;
    }

    @Override
    public final int nextDoc() throws IOException {

      if (lastReturn == DocIdSetIterator.NO_MORE_DOCS) {
        return DocIdSetIterator.NO_MORE_DOCS;
      }

      DocIdSetIterator dcit = iterators[0];
      int target = dcit.nextDoc();
      int size = iterators.length;
      int skip = 0;
      int i = 1;
      while (i < size) {
        if (i != skip) {
          dcit = iterators[i];
          int docid = dcit.advance(target);

          if (docid > target) {
            target = docid;
            if (i != 0) {
              skip = i;
              i = 0;
              continue;
            } else skip = 0;
          }
        }
        i++;
      }
      return (lastReturn = target);
    }

    @Override
    public final int advance(int target) throws IOException {

      if (lastReturn == DocIdSetIterator.NO_MORE_DOCS) {
        return DocIdSetIterator.NO_MORE_DOCS;
      }

      DocIdSetIterator dcit = iterators[0];
      target = dcit.advance(target);
      int size = iterators.length;
      int skip = 0;
      int i = 1;
      while (i < size) {
        if (i != skip) {
          dcit = iterators[i];
          int docid = dcit.advance(target);
          if (docid > target) {
            target = docid;
            if (i != 0) {
              skip = i;
              i = 0;
              continue;
            } else skip = 0;
          }
        }
        i++;
      }
      return (lastReturn = target);
    }

    @Override
    public long cost() {
      // TODO Auto-generated method stub
      return 0;
    }
  }

  @Override
  public final DocIdSetIterator iterator() throws IOException {
    return new AndDocIdSetIterator();
  }

  /**
   * Find existence in the set with index
   *
   * NOTE :  Expensive call. Avoid.
   * @param val value to find the index for
   * @return index where the value is
   */
  @Override
  public final int findWithIndex(int val) throws IOException {
    DocIdSetIterator finder = new AndDocIdSetIterator();
    int cursor = -1;
    try {
      int docid;
      while ((docid = finder.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
        if (docid > val) return -1;
        else if (docid == val) return ++cursor;
        else ++cursor;

      }
    } catch (IOException e) {
      return -1;
    }
    return -1;
  }

  @Override
  public final boolean find(int val) throws IOException {

    DocIdSetIterator finder = new AndDocIdSetIterator();

    try {
      int docid = finder.advance(val);
      if (docid != DocIdSetIterator.NO_MORE_DOCS && docid == val) return true;
      else return false;
    } catch (IOException e) {
      return false;
    }
  }

}
