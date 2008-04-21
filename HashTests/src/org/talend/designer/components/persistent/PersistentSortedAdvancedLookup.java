package org.talend.designer.components.persistent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.talend.designer.components.commons.AdvancedLookup.MATCHING_MODE;

import routines.system.IPersistableLookupRow;

public abstract class PersistentSortedAdvancedLookup<B extends Comparable<B> & IPersistableLookupRow<B>> implements IPersistableLookup<B>, IRowCreator<B> {

    List<B> list = new ArrayList<B>();

    Object[] arrayValues;

    boolean arrayIsDirty = true;

    List<B> listResult;

    B objectResult;

    MATCHING_MODE matchingMode;

    static final int ZERO = 0;

    static final int ONE = 1;

    IPersistentLookupManager<B> persistentSortedHash;

    String container;

    public PersistentSortedAdvancedLookup(MATCHING_MODE matchingMode, String container) {
        if (container == null) {
            throw new IllegalArgumentException("container can't be null");
        }
        this.container = container;

        persistentSortedHash = new PersistentSortedLookupManager<B>(matchingMode, container, this);

    }

    /**
     * @param container
     * @throws IOException
     * @see org.talend.designer.components.thash.io.hashimpl.SortedMultipleHashFile#initPut(java.lang.String)
     */
    public void initPut() throws IOException {
        this.persistentSortedHash.initPut();
    }

    public void put(B value) throws IOException {
        if (value != null) {
            persistentSortedHash.put(value);
        }
    }

    /**
     * @throws IOException
     * @see org.talend.designer.components.thash.io.hashimpl.SortedMultipleHashFile#endPut()
     */
    public void endPut() throws IOException {
        this.persistentSortedHash.endPut();
    }

    /**
     * @param container
     * @throws IOException
     * @throws IOException
     * @see org.talend.designer.components.thash.io.hashimpl.SortedMultipleHashFile#initGet(java.lang.String)
     */
    public void initGet() throws IOException {
        this.persistentSortedHash.initGet();
    }

    public void lookup(B key) throws IOException {
        persistentSortedHash.lookup(key);
    }

    public boolean hasNext() throws IOException {
        return persistentSortedHash.hasNext();
    }

    public B next() throws IOException {
        return persistentSortedHash.next();
    }

    /**
     * @param container
     * @throws IOException
     * @throws IOException
     * @see org.talend.designer.components.thash.io.hashimpl.SortedMultipleHashFile#endGet(java.lang.String)
     */
    public void endGet() throws IOException {
        this.persistentSortedHash.endGet();
    }

    public void clear() throws IOException {
    }

    public abstract B createRowInstance();

    public B getNextFreeRow() {
        return persistentSortedHash.getNextFreeRow();
    }
    
}
