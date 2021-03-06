/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2017 
 */
package com.ibm.streamsx.topology.internal.tester.tcp;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;

import com.ibm.streams.flow.declare.InputPortDeclaration;
import com.ibm.streams.flow.declare.OperatorGraph;
import com.ibm.streams.flow.declare.OperatorGraphFactory;
import com.ibm.streams.flow.declare.OperatorInvocation;
import com.ibm.streams.flow.declare.OutputPortDeclaration;
import com.ibm.streams.flow.handlers.StreamHandler;
import com.ibm.streams.flow.javaprimitives.JavaOperatorTester;
import com.ibm.streams.flow.javaprimitives.JavaTestableGraph;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.samples.operators.PassThrough;
import com.ibm.streamsx.topology.TStream;
import com.ibm.streamsx.topology.builder.BInputPort;
import com.ibm.streamsx.topology.builder.BOperatorInvocation;
import com.ibm.streamsx.topology.internal.tester.TestTupleInjector;
import com.ibm.streamsx.topology.internal.tester.TesterRuntime;
import com.ibm.streamsx.topology.internal.tester.TupleCollection;
import com.ibm.streamsx.topology.internal.tester.ops.TesterSink;

public class TCPTesterRuntime extends TesterRuntime {
    
    private OperatorGraph collectorGraph;
    private JavaTestableGraph localCollector;
    private Future<JavaTestableGraph> localRunningCollector;
    private TCPTestServer tcpServer;
    private BOperatorInvocation testerSinkOp;
    private final Map<TStream<?>, StreamTester> testers = new HashMap<>();
    
    private Map<Integer, TestTupleInjector> injectors = Collections
            .synchronizedMap(new HashMap<Integer, TestTupleInjector>());


    public TCPTesterRuntime(TupleCollection tester) {
        super(tester);
        // TODO Auto-generated constructor stub
    }
    
    


    // private SPLOperator testerSinkSplOp;

    /**
     * 
     * @param graphItems
     * @throws Exception
     */
    public void finalizeTester(Map<TStream<?>, Set<StreamHandler<Tuple>>> handlers)
            throws Exception {

        addTCPServerAndSink();
        collectorGraph = OperatorGraphFactory.newGraph();
        for (TStream<?> stream : handlers.keySet()) {
            int testerId = connectToTesterSink(stream);
            testers.put(stream, new StreamTester(collectorGraph, testerId,
                    stream));
        }

        localCollector = new JavaOperatorTester()
                .executable(collectorGraph);
        setupTestHandlers(handlers);
    }
    
    @Override
    public void start() {
        assert this.localCollector != null;
        localRunningCollector = localCollector.execute();
    }   
    
    /**
     * Add a TCP server that will list for tuples to be directed to handlers.
     * Adds a sink to the topology to capture those tuples and deliver them to
     * the current jvm to run Junit type tests.
     */
    private void addTCPServerAndSink() throws Exception {

        tcpServer = new TCPTestServer(0, new IoHandlerAdapter() {
            @Override
            public void messageReceived(IoSession session, Object message)
                    throws Exception {
                TestTuple tuple = (TestTuple) message;
                TestTupleInjector injector = injectors.get(tuple.getTesterId());
                injector.tuple(tuple.getTupleData());
            }
        });

        InetSocketAddress testAddr = tcpServer.start();
        addTesterSink(testAddr);
    }

    public void shutdown() throws Exception {
        tcpServer.shutdown();
        localRunningCollector.cancel(true);       
    }

    private void addTesterSink(InetSocketAddress testAddr) {

        Map<String, Object> hostInfo = new HashMap<>();
        hostInfo.put("host", testAddr.getHostString());
        hostInfo.put("port", testAddr.getPort());
        this.testerSinkOp = topology().builder().addOperator(TesterSink.class,
                hostInfo);

        /*
         * 
         * testerSinkOp = topology.graph().addOperator(TesterSink.class);
         * 
         * testerSinkSplOp = topology.splgraph().addOperator(testerSinkOp);
         * testerSinkOp.setStringParameter("host", testAddr.getHostString());
         * testerSinkOp.setIntParameter("port", testAddr.getPort());
         * 
         * Map<String, Object> params = new HashMap<>(); params.put("host",
         * testAddr.getHostString()); params.put("port", testAddr.getPort());
         * testerSinkSplOp.setParameters(params);
         */
    }
    
    /**
     * Connect a stream in the real topology to the TestSink operator that was
     * added.
     */
    private int connectToTesterSink(TStream<?> stream) {
        BInputPort inputPort = stream.connectTo(testerSinkOp, true, null);
        // testerSinkSplOp.addInput(inputPort);
        return inputPort.port().getPortNumber();
    }



    private void setupTestHandlers(Map<TStream<?>, Set<StreamHandler<Tuple>>> handlers) throws Exception {

        for (TStream<?> stream : handlers.keySet()) {
            Set<StreamHandler<Tuple>> streamHandlers = handlers.get(stream);
            StreamTester tester = testers.get(stream);

            StreamingOutput<OutputTuple> injectPort = localCollector
                    .getInputTester(tester.input);
            injectors.put(tester.testerId, new TestTupleInjector(injectPort));

            for (StreamHandler<Tuple> streamHandler : streamHandlers) {
                localCollector.registerStreamHandler(tester.output, streamHandler);
            }
        }
    }
    
    /**
     * Holds the information in the declared collector graph about the testers
     * so that the handlers can be attached once the graph is executed.
     */
    private static class StreamTester {
        final int testerId;
        final InputPortDeclaration input;
        final OutputPortDeclaration output;

        // In the graph executing locally, add a PassThrough operator that
        // the TCP server will inject tuples to. It's output will be where
        // the StreamHandlers are attached to.
        StreamTester(OperatorGraph graph, int testerId, TStream<?> stream) {
            this.testerId = testerId;
            OperatorInvocation<PassThrough> operator = graph
                    .addOperator(PassThrough.class);
            input = operator.addInput(stream.output().schema());
            output = operator.addOutput(stream.output().schema());
        }
    }
}
