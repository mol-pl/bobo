package com.browseengine.bobo.api;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.browseengine.bobo.facets.impl.PathFacetIterator;

public class MappedFacetAccessible implements FacetAccessible, Serializable {

	private static final long serialVersionUID = 1L;

	private final HashMap<String, BrowseFacet> _facetMap;
	private final BrowseFacet[] _facets;

	public MappedFacetAccessible(BrowseFacet[] facets) {
		_facetMap = new HashMap<>();
		for (BrowseFacet facet : facets) {
			_facetMap.put(facet.getValue(), facet);
		}
		_facets = facets;
	}

	@Override
	public BrowseFacet getFacet(String value) {
		return _facetMap.get(value);
	}

	@Override
	public int getFacetHitsCount(Object value) {
		BrowseFacet facet = _facetMap.get(value);
		if (facet != null) {
			return facet.getFacetValueHitCount();
		}
		return 0;
	}

	@Override
	public List<BrowseFacet> getFacets() {
		return Arrays.asList(_facets);
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
	}

	@Override
	public FacetIterator iterator() {
		return new PathFacetIterator(Arrays.asList(_facets));
	}

}
