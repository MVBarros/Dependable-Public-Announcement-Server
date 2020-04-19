package dpas.library;

import dpas.grpc.contract.Contract;
import dpas.grpc.contract.Contract.Announcement;
import dpas.grpc.contract.Contract.RegisterRequest;
import dpas.grpc.contract.ServiceDPASGrpc;
import dpas.utils.ContractGenerator;
import dpas.utils.link.PerfectStub;
import dpas.utils.link.QuorumStub;
import dpas.utils.link.RegisterStub;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.*;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoop;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.stub.StreamObserver;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Library {

    private final RegisterStub _stub;
    private final List<PerfectStub> _pstubs;
    private final List<ExecutorService> _executors;
    private final List<ManagedChannel> _channels;
    private final int _numFaults;

    public Library(String host, int port, PublicKey[] serverKey, int numFaults) {
        _channels = new ArrayList<>();
        _executors = new ArrayList<>();
        List<PerfectStub> stubs = new ArrayList<>();
        for (int i = 0; i < 3 * numFaults + 1; i++) {
            //One thread for each channel
            var executor = Executors.newSingleThreadExecutor();
            ManagedChannel channel = NettyChannelBuilder
                    .forAddress(host, port + i + 1)
                    .usePlaintext()
                    .executor(executor)
                    .build();
            _channels.add(channel);
            _executors.add(executor);
            var stub = ServiceDPASGrpc.newStub(channel);
            PerfectStub pStub = new PerfectStub(stub, serverKey[i]);
            stubs.add(pStub);
        }
        _pstubs = stubs;
        _stub = new RegisterStub(new QuorumStub(stubs, numFaults));
        _numFaults = numFaults;
    }

    public void finish() {
        _executors.forEach(ExecutorService::shutdownNow);
        _channels.forEach(ManagedChannel::shutdownNow);
    }


    public void register(PublicKey publicKey, PrivateKey privkey) {
        try {
            CountDownLatch latch = new CountDownLatch(2 * _numFaults + 1);
            RegisterRequest request = ContractGenerator.generateRegisterRequest(publicKey, privkey);
            for(var stub : _pstubs) {
                stub.registerWithException(request, new StreamObserver<>() {
                    @Override
                    public void onNext(Contract.MacReply value) {}

                    @Override
                    public void onError(Throwable t) {
                        System.out.println(t.getMessage());
                        latch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void post(PublicKey key, char[] message, Announcement[] a, PrivateKey privateKey) {
        try {
            _stub.post(key, privateKey, String.valueOf(message), a);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void postGeneral(PublicKey pubKey, char[] message, Announcement[] a, PrivateKey privateKey) {
        try {
            _stub.postGeneral(pubKey, privateKey, String.valueOf(message), a);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public Announcement[] read(PublicKey publicKey, int number) {
        try {
            return _stub.read(publicKey, number);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return new Announcement[0];
        }
    }

    public Announcement[] readGeneral(int number) {
        try {
            return _stub.readGeneral(number);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return new Announcement[0];
        }
    }
}
