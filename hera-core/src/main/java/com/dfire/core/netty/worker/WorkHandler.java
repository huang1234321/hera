package com.dfire.core.netty.worker;

import com.dfire.core.netty.listener.ResponseListener;
import com.dfire.core.message.Protocol.*;
import com.dfire.core.netty.worker.request.WorkExecuteJob;
import com.dfire.core.netty.worker.request.WorkHandleCancel;
import io.netty.channel.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.*;

/**
 * @author: <a href="mailto:lingxiao@2dfire.com">凌霄</a>
 * @time: Created in 1:32 2018/1/4
 * @desc SocketMessage为RPC消息体
 */
@Slf4j
public class WorkHandler extends SimpleChannelInboundHandler<SocketMessage> {


    private CompletionService<Response> completionService = new ExecutorCompletionService<Response>(Executors.newCachedThreadPool());
    private WorkContext workContext;

    public WorkHandler(final WorkContext workContext) {
        this.workContext = workContext;
        this.workContext.setHandler(this);
    }

    private List<ResponseListener> listeners = new CopyOnWriteArrayList<ResponseListener>();

    public void addListener(ResponseListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ResponseListener listener) {
        listeners.add(listener);
    }

    public SocketMessage wapper(Response response) {
        return SocketMessage
                .newBuilder()
                .setKind(SocketMessage.Kind.RESPONSE)
                .setBody(response.toByteString()).build();
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SocketMessage msg) throws Exception {
        SocketMessage socketMessage = (SocketMessage) msg;
        switch (socketMessage.getKind()) {
            case REQUEST:
                final Request request = Request.newBuilder().mergeFrom(socketMessage.getBody()).build();
                Operate operate = request.getOperate();
                if(operate == Operate.Schedule || operate == Operate.Manual || operate == Operate.Debug) {
                    completionService.submit(new Callable<Response>() {
                        private WorkExecuteJob workExecuteJob = new WorkExecuteJob();
                        @Override
                        public Response call() throws Exception {
                            return workExecuteJob.execute(workContext, request).get();
                        }
                    });
                } else if(operate == Operate.Cancel) {
                    completionService.submit(new Callable<Response>() {
                        private WorkHandleCancel workHandleCancel = new WorkHandleCancel();
                        @Override
                        public Response call() throws Exception {
                            return workHandleCancel.handleCancel(workContext, request).get();
                        }
                    });
                }
            case RESPONSE:
                final Response response = Response.newBuilder().mergeFrom(socketMessage.getBody()).build();
                for(ResponseListener listener : listeners) {
                    listener.onResponse(response);
                }
            case WEB_RESPONSE:
                final WebResponse webResponse = WebResponse.newBuilder().mergeFrom(socketMessage.getBody()).build();
                for(ResponseListener listener : listeners) {
                    listener.onWebResponse(webResponse);
                }

        }
        super.channelActive(ctx);
        ctx.writeAndFlush(msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("客户端与服务端连接开启");
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.close();
        log.info("客户端与服务端连接关闭");
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.info("work exception");
        super.exceptionCaught(ctx, cause);
    }

}
