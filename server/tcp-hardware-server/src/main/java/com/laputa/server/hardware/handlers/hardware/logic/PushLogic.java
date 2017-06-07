package com.laputa.server.hardware.handlers.hardware.logic;

import com.laputa.server.core.model.DashBoard;
import com.laputa.server.core.model.widgets.notifications.Notification;
import com.laputa.server.core.processors.NotificationBase;
import com.laputa.server.core.protocol.exceptions.NotificationBodyInvalidException;
import com.laputa.server.core.protocol.model.messages.StringMessage;
import com.laputa.server.core.session.HardwareStateHolder;
import com.laputa.server.notifications.push.GCMWrapper;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.laputa.server.core.protocol.enums.Response.NOTIFICATION_NOT_AUTHORIZED;
import static com.laputa.utils.LaputaByteBufUtil.*;

/**
 * Handler sends push notifications to Applications. Initiation is on hardware side.
 * Sends both to iOS and Android via Google Cloud Messaging service.
 *
 * The Laputa Project.
 * Created by Sommer
 * Created on 2/1/2015.
 *
 */
public class PushLogic extends NotificationBase {

    private static final Logger log = LogManager.getLogger(PushLogic.class);

    private final GCMWrapper gcmWrapper;

    public PushLogic(GCMWrapper gcmWrapper, long notificationQuotaLimit) {
        super(notificationQuotaLimit);
        this.gcmWrapper = gcmWrapper;
    }

    public void messageReceived(ChannelHandlerContext ctx, HardwareStateHolder state, StringMessage message) {
        if (Notification.isWrongBody(message.body)) {
            throw new NotificationBodyInvalidException();
        }

        DashBoard dash = state.user.profile.getDashByIdOrThrow(state.dashId);

        if (!dash.isActive) {
            log.debug("No active dashboard.");
            ctx.writeAndFlush(noActiveDash(message.id), ctx.voidPromise());
            return;
        }

        Notification widget = dash.getWidgetByType(Notification.class);

        if (widget == null || widget.hasNoToken()) {
            log.debug("User has no access token provided for push widget.");
            ctx.writeAndFlush(makeResponse(message.id, NOTIFICATION_NOT_AUTHORIZED), ctx.voidPromise());
            return;
        }

        final long now = System.currentTimeMillis();
        checkIfNotificationQuotaLimitIsNotReached(now);

        log.trace("Sending push for user {}, with message : '{}'.", state.user.email, message.body);
        widget.push(gcmWrapper, message.body, state.dashId);
        ctx.writeAndFlush(ok(message.id), ctx.voidPromise());
    }

}
