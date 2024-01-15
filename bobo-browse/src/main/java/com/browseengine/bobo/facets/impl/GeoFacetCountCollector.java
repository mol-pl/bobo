/**
 *
 */
package com.browseengine.bobo.facets.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.browseengine.bobo.api.BrowseFacet;
import com.browseengine.bobo.api.FacetIterator;
import com.browseengine.bobo.api.FacetSpec;
import com.browseengine.bobo.facets.FacetCountCollector;
import com.browseengine.bobo.facets.data.TermStringList;
import com.browseengine.bobo.facets.filter.GeoFacetFilter;
import com.browseengine.bobo.facets.impl.GeoFacetHandler.GeoFacetData;
import com.browseengine.bobo.util.BigFloatArray;
import com.browseengine.bobo.util.BigSegmentedArray;
import com.browseengine.bobo.util.GeoMatchUtil;
import com.browseengine.bobo.util.LazyBigIntArray;

public class GeoFacetCountCollector implements FacetCountCollector {

  private final String _name;
  private final FacetSpec _spec;
  private final BigSegmentedArray _count;
  private final int _countlength;
  private final TermStringList _predefinedRanges;
  private final GeoRange[] _ranges;
  private final BigFloatArray _xvals;
  private final BigFloatArray _yvals;
  private final BigFloatArray _zvals;
  // variable to specify if the geo distance calculations are in miles. Default is miles
  private final boolean _miles;

  public static class GeoRange {
    private final float _lat;
    private final float _lon;
    private final float _rad;

    public GeoRange(float lat, float lon, float radius) {
      _lat = lat;
      _lon = lon;
      _rad = radius;
    }

    /**
     * @return the latitude value
     */
    public float getLat() {
      return _lat;
    }

    /**
     * @return the longitude value
     */
    public float getLon() {
      return _lon;
    }

    /**
     * @return the radius
     */
    public float getRad() {
      return _rad;
    }
  }

  /**
   *
   * @param name 				name of the Geo Facet
   * @param dataCache			The data cache for the Geo Facet
   * @param docBase			the base doc id
   * @param fspec				the facet spec for this facet
   * @param predefinedRanges	List of ranges, where each range looks like: "lat, lon : radius".
   * @param miles        variable to specify if the geo distance calculations are in miles. False indicates distance calculation is in kilometers
   */
  protected GeoFacetCountCollector(String name, GeoFacetData dataCache, int docBase,
      FacetSpec fspec, List<String> predefinedRanges, boolean miles) {
    _name = name;
    _xvals = dataCache.get_xValArray();
    _yvals = dataCache.get_yValArray();
    _zvals = dataCache.get_zValArray();
    _spec = fspec;
    _predefinedRanges = new TermStringList();
    Collections.sort(predefinedRanges);
    _predefinedRanges.addAll(predefinedRanges);
    _countlength = predefinedRanges.size();
    _count = new LazyBigIntArray(_countlength);
    _ranges = new GeoRange[predefinedRanges.size()];
    int index = 0;
    for (String range : predefinedRanges) {
      _ranges[index++] = parse(range);
    }
    _miles = miles;
  }

  /**
   * @param docid The docid for which the facet counts are to be calculated
   */
  @Override
  public void collect(int docid) {
    float docX = _xvals.get(docid);
    float docY = _yvals.get(docid);
    float docZ = _zvals.get(docid);

    float radius, targetX, targetY, targetZ, delta;
    float xu, xl, yu, yl, zu, zl;
    int countIndex = -1;
    for (GeoRange range : _ranges) {
      // the countIndex for the count array should increment with the range index of the _ranges
      // array
      countIndex++;
      if (_miles) radius = GeoMatchUtil.getMilesRadiusCosine(range.getRad());
      else radius = GeoMatchUtil.getKMRadiusCosine(range.getRad());

      float[] coords = GeoMatchUtil.geoMatchCoordsFromDegrees(range.getLat(), range.getLon());
      targetX = coords[0];
      targetY = coords[1];
      targetZ = coords[2];

      if (_miles) delta = (range.getRad() / GeoMatchUtil.EARTH_RADIUS_MILES);
      else delta = (range.getRad() / GeoMatchUtil.EARTH_RADIUS_KM);

      xu = targetX + delta;
      xl = targetX - delta;

      // try to see if the range checks can short circuit the actual inCircle check
      if (docX > xu || docX < xl) continue;

      yu = targetY + delta;
      yl = targetY - delta;

      if (docY > yu || docY < yl) continue;

      zu = targetZ + delta;
      zl = targetZ - delta;

      if (docZ > zu || docZ < zl) continue;

      if (GeoFacetFilter.inCircle(docX, docY, docZ, targetX, targetY, targetZ, radius)) {
        // if the lat, lon values of this docid match the current user-specified range, then
        // increment the
        // appropriate count[] value
        _count.add(countIndex, _count.get(countIndex) + 1);
        // do not break here, since one document could lie in multiple user-specified ranges
      }
    }
  }

  @Override
  public void collectAll() {
    throw new UnsupportedOperationException("collectAll is not supported for Geo Facets yet");
  }

  /**
   * @return Count distribution for all the user specified range values
   */
  @Override
  public BigSegmentedArray getCountDistribution() {
    BigSegmentedArray dist = null;
    if (_predefinedRanges != null) {
      dist = new LazyBigIntArray(_predefinedRanges.size());
      int distIdx = 0;
      for (int i = 0; i < _count.size(); i++) {
        int count = _count.get(i);
        dist.add(distIdx++, count);
      }
    }
    return dist;
  }

  @Override
  public String getName() {
    return _name;
  }

  /**
   * @param value This value should be one of the user-specified ranges for this Facet Count Collector. Else an
   *              IllegalArgumentException will be raised
   * @return The BrowseFacet corresponding to the range value
   */
  @Override
  public BrowseFacet getFacet(String value) {
    if (_predefinedRanges != null) {
      int index = 0;
      if ((index = _predefinedRanges.indexOf(value)) != -1) {
        BrowseFacet choice = new BrowseFacet();
        choice.setFacetValueHitCount(_count.get(index));
        choice.setValue(value);
        return choice;
      } else {
        // user specified an unknown range value. the overhead to calculate the count for an unknown
        // range value is high,
        // in the sense it requires to go through each docid in the index. Till we get a better
        // solution, this operation is
        // unsupported
        throw new IllegalArgumentException(
            "The value argument is not one of the user-specified ranges");
      }
    } else {
      throw new IllegalArgumentException(
          "There are no user-specified ranges for this Facet Count Collector object");
    }
  }

  @Override
  public int getFacetHitsCount(Object value) {
    if (_predefinedRanges != null) {
      int index = 0;
      if ((index = _predefinedRanges.indexOf(value)) != -1) {
        return _count.get(index);
      } else {
        throw new IllegalArgumentException(
            "The value argument is not one of the user-specified ranges");
      }
    } else {
      throw new IllegalArgumentException(
          "There are no user-specified ranges for this Facet Count Collector object");
    }
  }

  /**
   * @return A list containing BrowseFacet objects for each of the user-specified ranges
   */
  @Override
  public List<BrowseFacet> getFacets() {
    if (_spec != null) {
      int minHitCount = _spec.getMinHitCount();
      if (_ranges != null) {
        List<BrowseFacet> facets = new ArrayList<BrowseFacet>();
        int countIndex = -1;
        for (String value : _predefinedRanges) {
          countIndex++;
          if (_count.get(countIndex) >= minHitCount) {
            BrowseFacet choice = new BrowseFacet();
            choice.setFacetValueHitCount(_count.get(countIndex));
            choice.setValue(value);
            facets.add(choice);
          }
        }
        return facets;
      } else {
        return FacetCountCollector.EMPTY_FACET_LIST;
      }
    } else {
      return FacetCountCollector.EMPTY_FACET_LIST;
    }
  }

  /**
   *
   * @param range Value should be of the format - lat , lon : radius
   * @return GeoRange object containing the lat, lon and radius value
   */
  public static GeoRange parse(String range) {
    String[] parts = range.split(":");
    if ((parts == null) || (parts.length != 2)) throw new IllegalArgumentException(
        "Range value not in the expected format(lat, lon : radius)");
    String coord_part = parts[0];
    float rad = Float.parseFloat(parts[1].trim());

    String[] coords = coord_part.split(",");
    if ((coords == null) || (coords.length != 2)) throw new IllegalArgumentException(
        "Range value not in the expected format(lat, lon : radius)");
    float lat = Float.parseFloat(coords[0].trim());
    float lon = Float.parseFloat(coords[1].trim());

    return new GeoRange(lat, lon, rad);
  }

  /*
   * (non-Javadoc)
   * @see com.browseengine.bobo.api.FacetAccessible#close()
   */
  @Override
  public void close() {
  }

  @Override
  public FacetIterator iterator() {
    return new DefaultFacetIterator(_predefinedRanges, _count, _countlength, true);
  }
}
