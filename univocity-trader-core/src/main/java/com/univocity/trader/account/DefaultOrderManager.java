package com.univocity.trader.account;

import com.univocity.trader.*;
import com.univocity.trader.indicators.base.*;
import org.slf4j.*;

import java.util.function.*;

public class DefaultOrderManager implements OrderManager {

	private static final Logger log = LoggerFactory.getLogger(DefaultOrderManager.class);
	private final TimeInterval maxTimeToKeepOrderOpen;

	public DefaultOrderManager() {
		this(TimeInterval.minutes(10));
	}

	public DefaultOrderManager(TimeInterval maxTimeToKeepOrderOpen) {
		this.maxTimeToKeepOrderOpen = maxTimeToKeepOrderOpen;
	}

	@Override
	public void prepareOrder(OrderBook book, OrderRequest order, Context context) {
		double originalPrice = order.getPrice();

		double availableQuantity = order.getQuantity();

		if (book != null && !book.isEmpty()) {
			double spread = book.getSpread(availableQuantity);
			double ask = book.getAverageAskAmount(availableQuantity);
			double bid = book.getAverageBidAmount(availableQuantity);

			//aims price at central price point of the spread.
			if (order.getSide() == Order.Side.BUY) {
				order.setPrice(bid + (spread / 2.0));
			} else {
				order.setPrice(ask - (spread / 2.0));
			}

			SymbolPriceDetails priceDetails = context.priceDetails();
			log.debug("{} - spread of {}: Ask {}, Bid {}. Closed at {}. Going to {} at ${}.",
					order.getSymbol(),
					priceDetails.priceToString(spread),
					priceDetails.priceToString(ask),
					priceDetails.priceToString(bid),
					priceDetails.priceToString(originalPrice),
					order.getSide(),
					priceDetails.priceToString(order.getPrice())
			);
		}
 	}

	@Override
	public void finalized(Order order, Trader trader) {
//		System.out.println(order.print(trader.getCandle().closeTime));
	}

	@Override
	public void updated(Order order, Trader trader, Consumer<Order> resubmission) {

	}

	@Override
	public void unchanged(Order order, Trader trader, Consumer<Order> resubmission) {
		if (isCancellable(order) && order.getTimeElapsed(trader.latestCandle().closeTime) >= maxTimeToKeepOrderOpen.ms) {
			order.cancel();
		}
	}

	@Override
	public boolean cancelToReleaseFundsFor(Order order, Trader currentTrader, Trader newSymbolTrader) {
		if (isCancellable(order) && order.getTimeElapsed(currentTrader.latestCandle().closeTime) > maxTimeToKeepOrderOpen.ms / 2) {
			order.cancel();
			if(order.isCancelled()) {
				return true;
			}
		}
		return false;
	}

	protected boolean isCancellable(Order order) {
		return order.getParent() == null && order.getTriggerCondition() == Order.TriggerCondition.NONE;
	}
}
