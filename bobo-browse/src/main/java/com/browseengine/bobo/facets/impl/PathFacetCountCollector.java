package com.browseengine.bobo.facets.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.browseengine.bobo.api.BrowseFacet;
import com.browseengine.bobo.api.BrowseSelection;
import com.browseengine.bobo.api.ComparatorFactory;
import com.browseengine.bobo.api.FacetIterator;
import com.browseengine.bobo.api.FacetSpec;
import com.browseengine.bobo.api.FacetSpec.FacetSortSpec;
import com.browseengine.bobo.facets.FacetCountCollector;
import com.browseengine.bobo.facets.data.FacetDataCache;
import com.browseengine.bobo.util.BigIntArray;
import com.browseengine.bobo.util.BigSegmentedArray;
import com.browseengine.bobo.util.BoundedPriorityQueue;
import com.browseengine.bobo.util.LazyBigIntArray;
import com.browseengine.bobo.util.ListMerger;

public class PathFacetCountCollector implements FacetCountCollector {
  private static final Logger log = LoggerFactory.getLogger(PathFacetCountCollector.class.getName());
  private final BrowseSelection _sel;
  protected BigSegmentedArray _count;
  private final String _name;
  private final String _sep;
  private final BigSegmentedArray _orderArray;
  protected final FacetDataCache<?> _dataCache;
  private final ComparatorFactory _comparatorFactory;
  private final int _minHitCount;
  private int _maxCount;
  private String[] _stringData;
  private final char[] _sepArray;
  private int _patStart;
  private int _patEnd;

  PathFacetCountCollector(String name, String sep, BrowseSelection sel, FacetSpec ospec,
      FacetDataCache<?> dataCache) {
    _sel = sel;
    _name = name;
    _dataCache = dataCache;
    _sep = sep;
    _sepArray = sep.toCharArray();
    _count = new LazyBigIntArray(_dataCache.freqs.length);
    log.info(name + ": " + _count.size());
    _orderArray = _dataCache.orderArray;
    _minHitCount = ospec.getMinHitCount();
    _maxCount = ospec.getMaxCount();
    if (_maxCount < 1) {
      _maxCount = _count.size();
    }
    FacetSortSpec sortOption = ospec.getOrderBy();
    switch (sortOption) {
    case OrderHitsDesc:
      _comparatorFactory = new FacetHitcountComparatorFactory();
      break;
    case OrderValueAsc:
      _comparatorFactory = null;
      break;
    case OrderByCustom:
      _comparatorFactory = ospec.getCustomComparatorFactory();
      break;
    default:
      throw new IllegalArgumentException("invalid sort option: " + sortOption);
    }
    Pattern.compile(_sep);
    _stringData = new String[10];
    _patStart = 0;
    _patEnd = 0;
  }

  @Override
  public BigSegmentedArray getCountDistribution() {
    return _count;
  }

  @Override
  public String getName() {
    return _name;
  }

  @Override
  public void collect(int docid) {
    int i = _orderArray.get(docid);
    _count.add(i, _count.get(i) + 1);
  }

  @Override
  public void collectAll() {
    _count = BigIntArray.fromArray(_dataCache.freqs);
  }

  @Override
  public BrowseFacet getFacet(String value) {
    return null;
  }

  @Override
  public int getFacetHitsCount(Object value) {
    return 0;
  }

  private void ensureCapacity(int minCapacity) {
    int oldCapacity = _stringData.length;
    if (minCapacity > oldCapacity) {
      Object oldData[] = _stringData;
      int newCapacity = (oldCapacity * 3) / 2 + 1;
      if (newCapacity < minCapacity) newCapacity = minCapacity;
      // minCapacity is usually close to size, so this is a win:
      _stringData = new String[newCapacity];
      System.arraycopy(oldData, 0, _stringData, Math.min(oldData.length, newCapacity), newCapacity);
    }
  }

  private int patListSize() {
    return (_patEnd - _patStart);
  }

  public boolean splitString(String input) {
    _patStart = 0;
    _patEnd = 0;
    char[] str = input.toCharArray();
    int index = 0;
    int sepindex = 0;
    int tokStart = -1;
    int tokEnd = 0;
    while (index < input.length()) {
      for (sepindex = 0; (sepindex < _sepArray.length)
          && (str[index + sepindex] == _sepArray[sepindex]); sepindex++)
        ;
      if (sepindex == _sepArray.length) {
        index += _sepArray.length;
        if (tokStart >= 0) {
          ensureCapacity(_patEnd + 1);
          tokEnd++;
          _stringData[_patEnd++] = input.substring(tokStart, tokEnd);
        }
        tokStart = -1;
      } else {
        if (tokStart < 0) {
          tokStart = index;
          tokEnd = index;
        } else {
          tokEnd++;
        }
        index++;
      }
    }

    if (_patEnd == 0) return false;

    if (tokStart >= 0) {
      ensureCapacity(_patEnd + 1);
      tokEnd++;
      _stringData[_patEnd++] = input.substring(tokStart, tokEnd);
    }

    // let gc do its job
    str = null;

    // Construct result
    while (_patEnd > 0 && _stringData[patListSize() - 1].equals("")) {
      _patEnd--;
    }
    return true;
  }

  private List<BrowseFacet> getFacetsForPath(String selectedPath, int depth, boolean strict,
      int minCount, int maxCount) {
    LinkedList<BrowseFacet> list = new LinkedList<BrowseFacet>();

    BoundedPriorityQueue<BrowseFacet> pq = null;
    if (_comparatorFactory != null) {
      final Comparator<BrowseFacet> comparator = _comparatorFactory.newComparator();

      pq = new BoundedPriorityQueue<BrowseFacet>(new Comparator<BrowseFacet>() {

        @Override
        public int compare(BrowseFacet o1, BrowseFacet o2) {
          return -comparator.compare(o1, o2);
        }

      }, maxCount);
    }

    String[] startParts = null;
    int startDepth = 0;

    if (selectedPath != null && selectedPath.length() > 0) {
      startParts = selectedPath.split(_sep);
      startDepth = startParts.length;
      if (!selectedPath.endsWith(_sep)) {
        selectedPath += _sep;
      }
    }

    String currentPath = null;
    int currentCount = 0;

    int wantedDepth = startDepth + depth;

    int index = 0;
    if (selectedPath != null && selectedPath.length() > 0) {
      index = _dataCache.valArray.indexOf(selectedPath);
      if (index < 0) {
        index = -(index + 1);
      }
    }

    StringBuffer buf = new StringBuffer();
    for (int i = index; i < _count.size(); ++i) {
      if (_count.get(i) >= minCount) {
        String path = _dataCache.valArray.get(i);
        // if (path==null || path.equals(selectedPath)) continue;

        int subCount = _count.get(i);

        // do not use Java split string in a loop !
        // String[] pathParts=path.split(_sep);
        int pathDepth = 0;
        if (!splitString(path)) {
          pathDepth = 0;
        } else {
          pathDepth = patListSize();
        }

        int tmpdepth = 0;
        if ((startDepth == 0) || (startDepth > 0 && path.startsWith(selectedPath))) {
          buf.delete(0, buf.length());
          int minDepth = Math.min(wantedDepth, pathDepth);
          tmpdepth = 0;
          for (int k = _patStart; ((k < _patEnd) && (tmpdepth < minDepth)); ++k, tmpdepth++) {
            buf.append(_stringData[k]);
            if (!_stringData[k].endsWith(_sep)) {
              if (pathDepth != wantedDepth || k < (wantedDepth - 1)) buf.append(_sep);
            }
          }
          String wantedPath = buf.toString();
          if (currentPath == null) {
            currentPath = wantedPath;
            currentCount = subCount;
          } else if (wantedPath.equals(currentPath)) {
            if (!strict) {
              currentCount += subCount;
            }
          } else {
            boolean directNode = false;

            if (wantedPath.endsWith(_sep)) {
              if (currentPath.equals(wantedPath.substring(0, wantedPath.length() - 1))) {
                directNode = true;
              }
            }

            if (strict) {
              if (directNode) {
                currentCount += subCount;
              } else {
                BrowseFacet ch = new BrowseFacet(currentPath, currentCount);
                if (pq != null) {
                  pq.add(ch);
                } else {
                  if (list.size() < maxCount) {
                    list.add(ch);
                  }
                }
                currentPath = wantedPath;
                currentCount = subCount;
              }
            } else {
              if (!directNode) {
                BrowseFacet ch = new BrowseFacet(currentPath, currentCount);
                if (pq != null) {
                  pq.add(ch);
                } else {
                  if (list.size() < maxCount) {
                    list.add(ch);
                  }
                }
                currentPath = wantedPath;
                currentCount = subCount;
              } else {
                currentCount += subCount;
              }
            }
          }
        } else {
          break;
        }
      }
    }

    if (currentPath != null && currentCount > 0) {
      BrowseFacet ch = new BrowseFacet(currentPath, currentCount);
      if (pq != null) {
        pq.add(ch);
      } else {
        if (list.size() < maxCount) {
          list.add(ch);
        }
      }
    }

    if (pq != null) {
      BrowseFacet val;
      while ((val = pq.poll()) != null) {
        list.addFirst(val);
      }
    }

    return list;
  }

  @Override
  public List<BrowseFacet> getFacets() {
    Properties props = _sel == null ? null : _sel.getSelectionProperties();
    int depth = PathFacetHandler.getDepth(props);
    boolean strict = PathFacetHandler.isStrict(props);

    String[] paths = _sel == null ? null : _sel.getValues();
    if (paths == null || paths.length == 0) {
      return getFacetsForPath(null, depth, strict, _minHitCount, _maxCount);
    }

    if (paths.length == 1) return getFacetsForPath(paths[0], depth, strict, _minHitCount, _maxCount);

    LinkedList<BrowseFacet> finalList = new LinkedList<BrowseFacet>();
    ArrayList<Iterator<BrowseFacet>> iterList = new ArrayList<Iterator<BrowseFacet>>(paths.length);
    for (String path : paths) {
      List<BrowseFacet> subList = getFacetsForPath(path, depth, strict, _minHitCount, _maxCount);
      if (subList.size() > 0) {
        iterList.add(subList.iterator());
      }
    }
    @SuppressWarnings("unchecked")
    Iterator<BrowseFacet> finalIter = ListMerger.mergeLists(iterList
        .toArray((Iterator<BrowseFacet>[]) new Iterator[iterList.size()]),
      _comparatorFactory == null ? new FacetValueComparatorFactory().newComparator()
          : _comparatorFactory.newComparator());
    while (finalIter.hasNext()) {
      BrowseFacet f = finalIter.next();
      finalList.addFirst(f);
    }
    return finalList;
  }

  @Override
  public void close() {
    // TODO Auto-generated method stub
  }

  @Override
  public FacetIterator iterator() {
    Properties props = _sel == null ? null : _sel.getSelectionProperties();
    int depth = PathFacetHandler.getDepth(props);
    boolean strict = PathFacetHandler.isStrict(props);
    List<BrowseFacet> finalList;

    String[] paths = _sel == null ? null : _sel.getValues();
    if (paths == null || paths.length == 0) {
      finalList = getFacetsForPath(null, depth, strict, Integer.MIN_VALUE, _count.size());
      return new PathFacetIterator(finalList);
    }

    if (paths.length == 1) {
      finalList = getFacetsForPath(paths[0], depth, strict, Integer.MIN_VALUE, _count.size());
      return new PathFacetIterator(finalList);
    }

    finalList = new LinkedList<BrowseFacet>();
    ArrayList<Iterator<BrowseFacet>> iterList = new ArrayList<Iterator<BrowseFacet>>(paths.length);
    for (String path : paths) {
      List<BrowseFacet> subList = getFacetsForPath(path, depth, strict, Integer.MIN_VALUE,
        _count.size());
      if (subList.size() > 0) {
        iterList.add(subList.iterator());
      }
    }

    @SuppressWarnings("unchecked")
    Iterator<BrowseFacet> finalIter = ListMerger.mergeLists(iterList
        .toArray((Iterator<BrowseFacet>[]) new Iterator[iterList.size()]),
      _comparatorFactory == null ? new FacetValueComparatorFactory().newComparator()
          : _comparatorFactory.newComparator());
    while (finalIter.hasNext()) {
      BrowseFacet f = finalIter.next();
      finalList.add(f);
    }
    return new PathFacetIterator(finalList);
  }
}
