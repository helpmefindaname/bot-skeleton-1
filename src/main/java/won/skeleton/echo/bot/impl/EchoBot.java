package won.skeleton.echo.bot.impl;

import won.bot.framework.bot.base.EventBot;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.EventBotAction;
import won.bot.framework.eventbot.action.impl.LogAction;
import won.bot.framework.eventbot.action.impl.MultipleActions;
import won.bot.framework.eventbot.action.impl.RandomDelayedAction;
import won.bot.framework.eventbot.action.impl.atomlifecycle.CreateEchoAtomWithSocketsAction;
import won.bot.framework.eventbot.action.impl.matcher.RegisterMatcherAction;
import won.bot.framework.eventbot.action.impl.wonmessage.ConnectWithAssociatedAtomAction;
import won.bot.framework.eventbot.action.impl.wonmessage.RespondWithEchoToMessageAction;
import won.bot.framework.eventbot.bus.EventBus;
import won.bot.framework.eventbot.event.impl.atomlifecycle.AtomCreatedEvent;
import won.bot.framework.eventbot.event.impl.lifecycle.ActEvent;
import won.bot.framework.eventbot.event.impl.matcher.AtomCreatedEventForMatcher;
import won.bot.framework.eventbot.event.impl.matcher.MatcherRegisterFailedEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.CloseFromOtherAtomEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.MessageFromOtherAtomEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.OpenFromOtherAtomEvent;
import won.bot.framework.eventbot.filter.impl.AtomUriInNamedListFilter;
import won.bot.framework.eventbot.filter.impl.NotFilter;
import won.bot.framework.eventbot.listener.BaseEventListener;
import won.bot.framework.eventbot.listener.impl.ActionOnEventListener;
import won.protocol.model.SocketType;

public class EchoBot extends EventBot {
    protected BaseEventListener atomCreator;
    protected BaseEventListener atomConnector;
    protected BaseEventListener autoResponder;
    protected BaseEventListener connectionCloser;

    private int registrationMatcherRetryInterval;

    public void setRegistrationMatcherRetryInterval(final int registrationMatcherRetryInterval) {
        this.registrationMatcherRetryInterval = registrationMatcherRetryInterval;
    }

    @Override
    protected void initializeEventListeners() {
        EventListenerContext ctx = getEventListenerContext();
        EventBus bus = getEventBus();
        // register with WoN nodes, be notified when new atoms are created
        RegisterMatcherAction registerMatcherAction = new RegisterMatcherAction(ctx);
        BaseEventListener matcherRegistrator = new ActionOnEventListener(ctx, registerMatcherAction, 1);
        bus.subscribe(ActEvent.class, matcherRegistrator);
        RandomDelayedAction delayedRegistration = new RandomDelayedAction(ctx, registrationMatcherRetryInterval,
                registrationMatcherRetryInterval, 0, registerMatcherAction);
        ActionOnEventListener matcherRetryRegistrator = new ActionOnEventListener(ctx, delayedRegistration);
        bus.subscribe(MatcherRegisterFailedEvent.class, matcherRetryRegistrator);
        // create the echo atom - if we're not reacting to the creation of our own echo
        // atom.
        this.atomCreator = new ActionOnEventListener(ctx,
                new NotFilter(new AtomUriInNamedListFilter(ctx,
                        ctx.getBotContextWrapper().getAtomCreateListName())),
                new CreateEchoAtomWithSocketsAction(ctx));
        bus.subscribe(AtomCreatedEventForMatcher.class, this.atomCreator);
        // as soon as the echo atom is created, connect to original
        this.atomConnector = new ActionOnEventListener(ctx, "atomConnector",
                new RandomDelayedAction(ctx, 5000, 5000, 1, new ConnectWithAssociatedAtomAction(ctx,
                        SocketType.ChatSocket.getURI(), SocketType.ChatSocket.getURI(),
                        "Greetings! I am the EchoBot! I will repeat everything you say, which you might "
                                + "find useful for testing purposes.")));
        bus.subscribe(AtomCreatedEvent.class, this.atomConnector);

        // add a listener that auto-responds to messages by a message
        // after 10 messages, it unsubscribes from all events
        // subscribe it to:
        // * message events - so it responds
        // * open events - so it initiates the chain reaction of responses
        this.autoResponder = new ActionOnEventListener(ctx, new RespondWithEchoToMessageAction(ctx));

        bus.subscribe(OpenFromOtherAtomEvent.class, this.autoResponder);
        bus.subscribe(MessageFromOtherAtomEvent.class, this.autoResponder);
        bus.subscribe(CloseFromOtherAtomEvent.class,
                new ActionOnEventListener(ctx, new LogAction(ctx, "received close message from remote atom.")));
    }
}
