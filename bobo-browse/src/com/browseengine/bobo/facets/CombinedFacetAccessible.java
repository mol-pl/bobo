package com.browseengine.bobo.facets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.browseengine.bobo.api.BrowseFacet;
import com.browseengine.bobo.api.FacetAccessible;
import com.browseengine.bobo.api.FacetSpec;
import com.browseengine.bobo.api.FacetVisitor;
import com.browseengine.bobo.api.FacetSpec.FacetSortSpec;
import com.browseengine.bobo.facets.impl.DefaultFacetVisitor;

public class CombinedFacetAccessible implements FacetAccessible {

	private final List<FacetAccessible> _list;
	private final FacetSpec _fspec;
	public CombinedFacetAccessible(FacetSpec fspec,List<FacetAccessible> list)
	{
		_list = list;
		_fspec = fspec;
	}
	
	public String toString() {
		return "_list:"+_list+" _fspec:"+_fspec;
	}
	
	public BrowseFacet getFacet(String value) {
		int sum=-1;
		String foundValue=null;
		if (_list!=null)
		{
			for (FacetAccessible facetAccessor : _list)
			{
				BrowseFacet facet = facetAccessor.getFacet(value);
				if (facet!=null)
				{
				  foundValue = facet.getValue();
					if (sum==-1) sum=facet.getHitCount();
					else sum+=facet.getHitCount();
				}
			}
		}
		if (sum==-1) return null;
		return new BrowseFacet(foundValue,sum);
	}

	public List<BrowseFacet> getFacets() {
//		Map<String,BrowseFacet> facetMap;
//		if (FacetSortSpec.OrderValueAsc.equals(_fspec.getOrderBy()))
//		{
//			facetMap= new TreeMap<String,BrowseFacet>();
//		}
//		else
//		{
//			facetMap = new HashMap<String,BrowseFacet>();
//		}
//		
		// instantiate a facet visitor for the list of facetaccessibles
		DefaultFacetVisitor visitor = new DefaultFacetVisitor();
		for (FacetAccessible facetAccessor : _list)
		{
//			Iterator<BrowseFacet> iter = facetAccessor.getFacets().iterator();
//			if (facetMap.size() == 0)
//			{
//				while(iter.hasNext())
//				{
//					BrowseFacet facet = iter.next();
//					facetMap.put(facet.getValue(),facet);
//				}
//			}
//			else
//			{
//				while(iter.hasNext())
//				{
//					BrowseFacet facet = iter.next();
//					BrowseFacet existing = facetMap.get(facet.getValue());
//					if (existing == null)
//					{
//						facetMap.put(facet.getValue(), facet);
//					}
//					else
//					{
//						existing.setHitCount(existing.getHitCount() + facet.getHitCount());
//					}
//				}
//			}
			facetAccessor.visitFacets(visitor);
		}
		
		// create the final list of BrowseFacets only once for all the facet accessibles
        int cnt = 0;
        int maxCnt = _fspec.getMaxCount();
        if(maxCnt <= 0) maxCnt = Integer.MAX_VALUE;
        int minHits = _fspec.getMinHitCount();
        List<BrowseFacet> list = new LinkedList<BrowseFacet>();
		Map<String,Integer> facetMap = visitor.getFacetMap();
		
		if (FacetSortSpec.OrderValueAsc.equals(_fspec.getOrderBy()))
		{
		  for(String facet : facetMap.keySet())
		  {
			  int hitcount = facetMap.get(facet);
			  if(hitcount >= minHits)
			  {
				  list.add(new BrowseFacet(facet, hitcount));
				  if(++cnt >= maxCnt) break;			      
			  }
		  }
		}
		else
		{
		  Comparator<BrowseFacet> comparator;
		  if (FacetSortSpec.OrderHitsDesc.equals(_fspec.getOrderBy()))
		  {
		    comparator = new Comparator<BrowseFacet>()
		    {
		      public int compare(BrowseFacet f1, BrowseFacet f2)
		      {
		        int val=f2.getHitCount() - f1.getHitCount();
		        if (val==0)
		        {
		          val = (f1.getValue().compareTo(f2.getValue()));
		        }
		        return val;
		      }
            };
		  }
		  else // FacetSortSpec.OrderByCustom.equals(_fspec.getOrderBy()
		  {
		    comparator = _fspec.getCustomComparatorFactory().newComparator();
		  }
		  ArrayList<BrowseFacet> facets = new ArrayList<BrowseFacet>();
		  for(String facet : facetMap.keySet()) {
			  facets.add(new BrowseFacet(facet, facetMap.get(facet)));
		  }
		  Collections.sort(facets, comparator);
		  for(BrowseFacet facet : facets)
		  {
		    if(facet.getHitCount() >= minHits)
		    {
		      list.add(facet);
		      if(++cnt >= maxCnt) break;                  
		    }
		  }
		}
		return list;
	}

	/**
	 * 	@see com.browseengine.bobo.api.FacetAccessible.visitFacets()		
	 */
	public void visitFacets(FacetVisitor visitor) {
		for (FacetAccessible facetAccessor : _list) {
			facetAccessor.visitFacets(visitor);			
		}
	}

}
