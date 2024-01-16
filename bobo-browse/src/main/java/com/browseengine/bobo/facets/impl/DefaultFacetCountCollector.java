package com.browseengine.bobo.facets.impl;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.browseengine.bobo.api.BrowseFacet;
import com.browseengine.bobo.api.BrowseSelection;
import com.browseengine.bobo.api.ComparatorFactory;
import com.browseengine.bobo.api.FacetIterator;
import com.browseengine.bobo.api.FacetSpec;
import com.browseengine.bobo.api.FacetSpec.FacetSortSpec;
import com.browseengine.bobo.api.FieldValueAccessor;
import com.browseengine.bobo.facets.FacetCountCollector;
import com.browseengine.bobo.facets.data.FacetDataCache;
import com.browseengine.bobo.facets.data.TermDoubleList;
import com.browseengine.bobo.facets.data.TermFloatList;
import com.browseengine.bobo.facets.data.TermIntList;
import com.browseengine.bobo.facets.data.TermLongList;
import com.browseengine.bobo.facets.data.TermShortList;
import com.browseengine.bobo.facets.data.TermValueList;
import com.browseengine.bobo.jmx.JMXUtil;
import com.browseengine.bobo.util.BigSegmentedArray;
import com.browseengine.bobo.util.IntBoundedPriorityQueue;
import com.browseengine.bobo.util.IntBoundedPriorityQueue.IntComparator;
import com.browseengine.bobo.util.LazyBigIntArray;
import com.browseengine.bobo.util.MemoryManager;
import com.browseengine.bobo.util.MemoryManagerAdminMBean;

public abstract class DefaultFacetCountCollector implements FacetCountCollector {
  private static final Logger log = LoggerFactory.getLogger(DefaultFacetCountCollector.class.getName());
  protected final FacetSpec _ospec;
  public BigSegmentedArray _count;

  public int _countlength;
  protected FacetDataCache<?> _dataCache;
  private final String _name;
  protected final BrowseSelection _sel;
  protected final BigSegmentedArray _array;
  protected final LinkedList<BigSegmentedArray> intarraylist = new LinkedList<>();
  private boolean _closed = false;

  protected static MemoryManager<BigSegmentedArray> intarraymgr = new MemoryManager<>(
      new MemoryManager.Initializer<BigSegmentedArray>() {
        @Override
        public void init(BigSegmentedArray buf) {
          buf.fill(0);
        }

        @Override
        public BigSegmentedArray newInstance(int size) {
          return new LazyBigIntArray(size);
        }

        @Override
        public int size(BigSegmentedArray buf) {
          assert buf != null;
          return buf.size();
        }

      });

  static {
    try {
      // register memory manager mbean
      MBeanServer mbeanServer = java.lang.management.ManagementFactory.getPlatformMBeanServer();
      ObjectName mbeanName = new ObjectName(JMXUtil.JMX_DOMAIN, "name",
          "DefaultFacetCountCollector-MemoryManager");
      StandardMBean mbean = new StandardMBean(intarraymgr.getAdminMBean(),
          MemoryManagerAdminMBean.class);
      mbeanServer.registerMBean(mbean, mbeanName);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
  }

  public DefaultFacetCountCollector(String name, FacetDataCache<?> dataCache, int docBase,
      BrowseSelection sel, FacetSpec ospec) {
    _sel = sel;
    _ospec = ospec;
    _name = name;
    _dataCache = dataCache;
    _countlength = _dataCache.freqs.length;

    if (_dataCache.freqs.length <= 3096) {
      _count = new LazyBigIntArray(_countlength);
    } else {
      _count = intarraymgr.get(_countlength);
      intarraylist.add(_count);
    }

    _array = _dataCache.orderArray;
  }

  @Override
  public String getName() {
    return _name;
  }

  @Override
  abstract public void collect(int docid);

  @Override
  abstract public void collectAll();

  @Override
  public BrowseFacet getFacet(String value) {
    if (_closed) {
      throw new IllegalStateException("This instance of count collector for " + _name
          + " was already closed");
    }
    BrowseFacet facet = null;
    int index = _dataCache.valArray.indexOf(value);
    if (index >= 0) {
      facet = new BrowseFacet(_dataCache.valArray.get(index), _count.get(index));
    } else {
      facet = new BrowseFacet(_dataCache.valArray.format(value), 0);
    }
    return facet;
  }

  @Override
  public int getFacetHitsCount(Object value) {
    if (_closed) {
      throw new IllegalStateException("This instance of count collector for " + _name
          + " was already closed");
    }
    int index = _dataCache.valArray.indexOf(value);
    if (index >= 0) {
      return _count.get(index);
    } else {
      return 0;
    }
  }

  @Override
  public BigSegmentedArray getCountDistribution() {
    return _count;
  }

  public FacetDataCache<?> getFacetDataCache() {
    return _dataCache;
  }

  public static List<BrowseFacet> getFacets(FacetSpec ospec, BigSegmentedArray count,
      int countlength, final TermValueList<?> valList) {
    if (ospec != null) {
      int minCount = ospec.getMinHitCount();
      int max = ospec.getMaxCount();
      if (max <= 0) max = countlength;

      List<BrowseFacet> facetColl;
      FacetSortSpec sortspec = ospec.getOrderBy();
      if (sortspec == FacetSortSpec.OrderValueAsc) {
        facetColl = new ArrayList<>(max);
        for (int i = 1; i < countlength; ++i) // exclude zero
        {
          int hits = count.get(i);
          if (hits >= minCount) {
            BrowseFacet facet = new BrowseFacet(valList.get(i), hits);
            facetColl.add(facet);
          }
          if (facetColl.size() >= max) break;
        }
      } else // if (sortspec == FacetSortSpec.OrderHitsDesc)
      {
        ComparatorFactory comparatorFactory;
        if (sortspec == FacetSortSpec.OrderHitsDesc) {
          comparatorFactory = new FacetHitcountComparatorFactory();
        } else {
          comparatorFactory = ospec.getCustomComparatorFactory();
        }

        if (comparatorFactory == null) {
          throw new IllegalArgumentException("facet comparator factory not specified");
        }

        final IntComparator comparator = comparatorFactory.newComparator(new FieldValueAccessor() {

          @Override
          public String getFormatedValue(int index) {
            return valList.get(index);
          }

          @Override
          public Object getRawValue(int index) {
            return valList.getRawValue(index);
          }

        }, count);
        facetColl = new LinkedList<>();
        final int forbidden = -1;
        IntBoundedPriorityQueue pq = new IntBoundedPriorityQueue(comparator, max, forbidden);

        for (int i = 1; i < countlength; ++i) {
          int hits = count.get(i);
          if (hits >= minCount) {
            pq.offer(i);
          }
        }

        int val;
        while ((val = pq.pollInt()) != forbidden) {
          BrowseFacet facet = new BrowseFacet(valList.get(val), count.get(val));
          ((LinkedList<BrowseFacet>) facetColl).addFirst(facet);
        }
      }
      return facetColl;
    } else {
      return FacetCountCollector.EMPTY_FACET_LIST;
    }
  }

  @Override
  public List<BrowseFacet> getFacets() {
    if (_closed) {
      throw new IllegalStateException("This instance of count collector for " + _name
          + " was already closed");
    }

    return getFacets(_ospec, _count, _countlength, _dataCache.valArray);

  }

  @Override
  public void close() {
    if (_closed) {
      log.warn("This instance of count collector for '" + _name
          + "' was already closed. This operation is no-op.");
      return;
    }
    _closed = true;
    while (!intarraylist.isEmpty()) {
      intarraymgr.release(intarraylist.poll());
    }
  }

  /**
   * This function returns an Iterator to visit the facets in value order
   * @return	The Iterator to iterate over the facets in value order
   */
  @Override
  public FacetIterator iterator() {
    if (_closed) {
      throw new IllegalStateException("This instance of count collector for '" + _name
          + "' was already closed");
    }
    if (_dataCache.valArray.getType().equals(Integer.class)) {
      return new DefaultIntFacetIterator((TermIntList) _dataCache.valArray, _count, _countlength,
          false);
    } else if (_dataCache.valArray.getType().equals(Long.class)) {
      return new DefaultLongFacetIterator((TermLongList) _dataCache.valArray, _count, _countlength,
          false);
    } else if (_dataCache.valArray.getType().equals(Short.class)) {
      return new DefaultShortFacetIterator((TermShortList) _dataCache.valArray, _count,
          _countlength, false);
    } else if (_dataCache.valArray.getType().equals(Float.class)) {
      return new DefaultFloatFacetIterator((TermFloatList) _dataCache.valArray, _count,
          _countlength, false);
    } else if (_dataCache.valArray.getType().equals(Double.class)) {
      return new DefaultDoubleFacetIterator((TermDoubleList) _dataCache.valArray, _count,
          _countlength, false);
    } else return new DefaultFacetIterator(_dataCache.valArray, _count, _countlength, false);
  }
}
