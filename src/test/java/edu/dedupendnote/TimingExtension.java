package edu.dedupendnote;

import java.lang.reflect.Method;
import java.util.logging.Logger;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

public class TimingExtension
		implements BeforeTestExecutionCallback, AfterTestExecutionCallback, BeforeAllCallback, AfterAllCallback {

	private static final Logger logger = Logger.getLogger(TimingExtension.class.getName());

	private long allStartTime = 0L;

	private static final String START_TIME = "start time";

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		allStartTime = System.currentTimeMillis();
	}

	@Override
	public void beforeTestExecution(ExtensionContext context) throws Exception {
		getStore(context).put(START_TIME, System.currentTimeMillis());
	}

	@Override
	public void afterAll(ExtensionContext context) throws Exception {
		long duration = System.currentTimeMillis() - allStartTime;

		logger.info(() -> "Whole test class took %s ms.".formatted(duration));
	}

	@Override
	public void afterTestExecution(ExtensionContext context) throws Exception {
		Method testMethod = context.getRequiredTestMethod();
		long startTime = getStore(context).remove(START_TIME, long.class);
		long duration = System.currentTimeMillis() - startTime;

		logger.info(() -> "Method [%s] took %s ms.".formatted(testMethod.getName(), duration));
	}

	private Store getStore(ExtensionContext context) {
		return context.getStore(Namespace.create(getClass(), context.getRequiredTestMethod()));
	}

}
