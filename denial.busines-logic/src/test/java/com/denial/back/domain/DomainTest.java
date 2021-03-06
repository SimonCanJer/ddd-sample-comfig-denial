package com.denial.back.domain;

import com.back.api.IDataHolder;
import com.back.api.IPipeline;
import com.back.domain.ConcreteDomainObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DomainTest {
    final int MODULO = 5;
    static class ConcreteDomainImpl extends ConcreteDomainObject
    {
        Map<String, IDataHolder.CallTrailer> trailers= new ConcurrentHashMap<>();

        IDataHolder holder= new IDataHolder() {


            @Override
            public VarResult proceedRequest(String id, IPipeline pipeline, BiConsumer<Map<String, Serializable>, String> operation, Supplier<CallTrailer> onNull, String command) {
                return pipeline.apply(trailers.computeIfAbsent(id,(k)->{return onNull.get();}),operation, command);

            }

            @Override
            public int registeredClients() {
                return trailers.size();
            }

            @Override
            public IDataHolder init() {
                return this;
            }

            @Override
            public IDataHolder shutdown() {
                return this;
            }
        };

        @Override
        protected IDataHolder getDataPopulator() {
            return holder;
        }
    }
    ConcreteDomainImpl d;
///We test only unit of Domain, not TestTrail
    @Before
    public void init()
    {
        d= new ConcreteDomainImpl();
    }
    @Test
    public void process() {

       testDomainCommon(1000, 10L, null);
    }
    static void inspectDomainState(ConcreteDomainImpl d)
    {
        d.trailers.values().stream().forEach((t)->{
            Assert.assertTrue("chech counter>5",t.getCounter()>5);
        });
    }
    @Test
    public void processIntegrated() {

        testDomainCommon(1000, 10L, DomainTest::inspectDomainState );


    }
    private void testDomainCommon(int cycles, long sleep, Consumer<ConcreteDomainImpl> fineCheck) {
        BlockingQueue<Runnable> q = new LinkedBlockingQueue<>();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 100, TimeUnit.SECONDS, q);
        AtomicInteger counter = new AtomicInteger();
        for(int i=0;i<cycles;i++)
        {
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            executor.execute(()->{

                int count= counter.getAndIncrement();
                String s="id="+count%MODULO;
                d.process(s,"val=1; return ++val");

            });
        }
        while(counter.get()<999)
        {

        }
        Assert.assertEquals("must be "+MODULO+"client trailers",MODULO,d.getDataPopulator().registeredClients());
        executor.shutdownNow();
        if(fineCheck!=null)
           fineCheck.accept(d);
    }
}