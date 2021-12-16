package com.github.unidbg.thread;

import com.github.unidbg.AbstractEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.arm.ARM;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.Arm64Const;
import unicorn.ArmConst;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract class AbstractTask implements Task {

    private static final Log log = LogFactory.getLog(AbstractTask.class);

    @Override
    public final boolean canDispatch() {
        return true;
    }

    private long context;

    protected final boolean isContextSaved() {
        return this.context != 0;
    }

    private final List<SignalTask> signalTaskList = new ArrayList<>();

    @Override
    public void addSignalTask(SignalTask task) {
        signalTaskList.add(task);
    }

    @Override
    public void removeSignalTask(SignalTask task) {
        signalTaskList.remove(task);
    }

    @Override
    public List<SignalTask> getSignalTaskList() {
        return signalTaskList.isEmpty() ? Collections.<SignalTask>emptyList() : new ArrayList<>(signalTaskList);
    }

    protected final Number continueRun(AbstractEmulator<?> emulator, long until) {
        Backend backend = emulator.getBackend();
        backend.context_restore(this.context);
        long pc = backend.reg_read(emulator.is32Bit() ? ArmConst.UC_ARM_REG_PC : Arm64Const.UC_ARM64_REG_PC).longValue();
        if (emulator.is32Bit()) {
            pc &= 0xffffffffL;
            if (ARM.isThumb(backend)) {
                pc += 1;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("continue run task=" + this + ", pc=" + UnidbgPointer.pointer(emulator, pc) + ", until=0x" + Long.toHexString(until));
        }
        return emulator.emulate(pc, until);
    }

    @Override
    public final void saveContext(AbstractEmulator<?> emulator) {
        Backend backend = emulator.getBackend();
        if (this.context == 0) {
            this.context = backend.context_alloc();
        }
        backend.context_save(this.context);
    }

    @Override
    public void destroy(AbstractEmulator<?> emulator) {
        Backend backend = emulator.getBackend();
        if (this.context != 0) {
            backend.context_free(this.context);
            this.context = 0;
        }
        if (stackBlock != null) {
            stackBlock.free();
            stackBlock = null;
        }
    }

    public static final int THREAD_STACK_SIZE = 0x80000;

    private MemoryBlock stackBlock;

    protected final UnidbgPointer allocateStack(Emulator<?> emulator) {
        if (stackBlock == null) {
            stackBlock = emulator.getMemory().malloc(THREAD_STACK_SIZE, true);
        }
        return stackBlock.getPointer().share(THREAD_STACK_SIZE, 0);
    }

}