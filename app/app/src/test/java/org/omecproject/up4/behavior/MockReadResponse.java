package org.omecproject.up4.behavior;

import org.onosproject.net.pi.runtime.PiCounterCell;
import org.onosproject.net.pi.runtime.PiCounterCellData;
import org.onosproject.net.pi.runtime.PiCounterCellHandle;
import org.onosproject.net.pi.runtime.PiEntity;
import org.onosproject.net.pi.runtime.PiEntityType;
import org.onosproject.net.pi.runtime.PiHandle;
import org.onosproject.p4runtime.api.P4RuntimeReadClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * For faking reads to a p4runtime client. Currently only used for testing UP4-specific counter reads, because all other
 * P4 entities that UP4 reads can be read via other ONOS services.
 */
public class MockReadResponse implements P4RuntimeReadClient.ReadResponse {
    List<PiEntity> entities;

    @Override
    public boolean isSuccess() {
        return true;
    }

    public MockReadResponse(Iterable<? extends PiHandle> handles) {
        this.entities = new ArrayList<>();
        checkNotNull(handles);
        handles.forEach(this::handle);
    }

    public MockReadResponse handle(PiHandle handle) {
        if (handle.entityType().equals(PiEntityType.COUNTER_CELL)) {
            PiCounterCellHandle counterHandle = (PiCounterCellHandle) handle;
            long counterIndex = counterHandle.cellId().index();
            PiCounterCellData data = new PiCounterCellData(TestConstants.COUNTER_PKTS, TestConstants.COUNTER_BYTES);
            PiEntity entity = new PiCounterCell(counterHandle.cellId(), data);
            this.entities.add(entity);

        }
        // Only handles counter cell so far

        return this;
    }

    @Override
    public Collection<PiEntity> all() {
        return this.entities;
    }

    @Override
    public <E extends PiEntity> Collection<E> all(Class<E> clazz) {
        List<E> results = new ArrayList<>();
        this.entities.forEach(ent -> {
            if (ent.getClass().equals(clazz)) {
                results.add(clazz.cast(ent));
            }
        });
        return results;
    }

    @Override
    public String explanation() {
        return null;
    }

    @Override
    public Throwable throwable() {
        return null;
    }
}
