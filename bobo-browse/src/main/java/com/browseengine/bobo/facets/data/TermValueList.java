package com.browseengine.bobo.facets.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * List of terms.
 * 
 * This class behaves as List of String with a few extensions:
 * <ul>
 * <li> Semi-immutable, e.g. once added, cannot be removed. </li>
 * <li> Assumes sequence of values added are in sorted order </li>
 * <li> {@link #indexOf(Object)} return value conforms to the contract of {@link Arrays#binarySearch(Object[], Object)}</li>
 * <li> {@link #seal()} is introduce to trim the List size, similar to {@link ArrayList#trimToSize()}, once it is called, no add should be performed.</li>
 * </ul>
 */
public abstract class TermValueList<T> implements List<String> {

	protected abstract List<?> buildPrimitiveList(int capacity);

	protected Class<T> _type;

	public abstract String format(Object o);

	public abstract void seal();

	protected List<?> _innerList;

	protected TermValueList() {
		_innerList = buildPrimitiveList(-1);
	}

	protected TermValueList(int capacity) {
		_innerList = buildPrimitiveList(capacity);
	}

	/**
	 * The user of this method should not try to alter the content of the list,
	 * which may result in data inconsistency.
	 * And of the content can be accessed using the getRawValue(int) method.
	 *
	 * @return the inner list
	 */
	public List<?> getInnerList() {
		return _innerList;
	}

	/**
	 * Add a new value to the list. <b>It is important to add the values in sorted (ASC) order.</b>
	 * Our algorithm uses binary searches and priority queues, both of which fails when the ordering is wrong.
	 */
	@Override
	abstract public boolean add(String o);

	@Override
	public void add(int index, String element) {
		throw new IllegalStateException("not supported");
	}

	@Override
	public boolean addAll(Collection<? extends String> c) {
		boolean ret = true;
		for (String s : c) {
			ret &= add(s);
		}
		return ret;
	}

	@Override
	public boolean addAll(int index, Collection<? extends String> c) {
		throw new IllegalStateException("not supported");
	}

	@Override
	public void clear() {
		_innerList.clear();
	}

	@Override
	public boolean contains(Object o) {
		return indexOf(o) >= 0;
	}

	public abstract boolean containsWithType(T val);

	@Override
	public boolean containsAll(Collection<?> c) {
		throw new IllegalStateException("not supported");
	}

	public Class<T> getType() {
		return _type;
	}

	@Override
	public String get(int index) {
		return format(_innerList.get(index));
	}

	@SuppressWarnings("unchecked")
	public T getRawValue(int index) {
		return (T) _innerList.get(index);
	}

	public Comparable<?> getComparableValue(int index) {
		return (Comparable<?>) _innerList.get(index);
	}

	@Override
	abstract public int indexOf(Object o);

	public int indexOfWithOffset(Object value, int offset) {
		throw new IllegalStateException("not supported");
	}

	public abstract int indexOfWithType(T o);

	@Override
	public boolean isEmpty() {
		return _innerList.isEmpty();
	}

	@Override
	public Iterator<String> iterator() {
		final Iterator<?> iter = _innerList.iterator();

		return new Iterator<>() {
			@Override
			public boolean hasNext() {
				return iter.hasNext();
			}

			@Override
			public String next() {
				return format(iter.next());
			}

			@Override
			public void remove() {
				iter.remove();
			}
		};
	}

	@Override
	public int lastIndexOf(Object o) {
		return indexOf(o);
	}

	@Override
	public ListIterator<String> listIterator() {
		throw new IllegalStateException("not supported");
	}

	@Override
	public ListIterator<String> listIterator(int index) {
		throw new IllegalStateException("not supported");
	}

	@Override
	public boolean remove(Object o) {
		throw new IllegalStateException("not supported");
	}

	@Override
	public String remove(int index) {
		throw new IllegalStateException("not supported");
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new IllegalStateException("not supported");
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new IllegalStateException("not supported");
	}

	@Override
	public String set(int index, String element) {
		throw new IllegalStateException("not supported");
	}

	@Override
	public int size() {
		return _innerList.size();
	}

	@Override
	public List<String> subList(int fromIndex, int toIndex) {
		throw new IllegalStateException("not supported");
	}

	@Override
	public Object[] toArray() {
		Object[] array = _innerList.toArray();
		Object[] retArray = new Object[array.length];
		for (int i = 0; i < array.length; ++i) {
			retArray[i] = format(array[i]);
		}
		return retArray;
	}

	@Override
	public <R> R[] toArray(R[] a) {
		List<String> l = subList(0, size());
		return l.toArray(a);
	}

	public static void main(String[] args) {
		int numIter = 20000;
		TermIntList list = new TermIntList();
		for (int i = 0; i < numIter; ++i) {
			list.add(String.valueOf(i));
		}
		long start = System.currentTimeMillis();
		List<?> rawList = list.getInnerList();
		for (int i = 0; i < numIter; ++i) {
			rawList.get(i);
		}
		long end = System.currentTimeMillis();
		System.out.println("took: " + (end - start));
	}
}
