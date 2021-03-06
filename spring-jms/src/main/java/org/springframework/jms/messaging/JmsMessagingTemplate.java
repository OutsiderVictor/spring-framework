/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.jms.messaging;

import java.util.Map;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessagingMessageConverter;
import org.springframework.jms.support.converter.SimpleJmsHeaderMapper;
import org.springframework.jms.support.converter.SimpleMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.core.AbstractMessageReceivingTemplate;
import org.springframework.messaging.core.MessagePostProcessor;
import org.springframework.util.Assert;

/**
 * An implementation of {@link JmsMessageOperations}.
 *
 * @author Stephane Nicoll
 * @since 4.1
 */
public class JmsMessagingTemplate
		extends AbstractMessageReceivingTemplate<Destination>
		implements JmsMessageOperations, InitializingBean {

	private JmsTemplate jmsTemplate;

	private MessageConverter jmsMessageConverter = new MessagingMessageConverter(
			new SimpleMessageConverter(), new SimpleJmsHeaderMapper());

	private String defaultDestinationName;

	public JmsMessagingTemplate() {
	}

	/**
	 * Create an instance with the {@link JmsTemplate} to use.
	 */
	public JmsMessagingTemplate(JmsTemplate jmsTemplate) {
		Assert.notNull("JmsTemplate must not be null");
		this.jmsTemplate = jmsTemplate;
	}

	/**
	 * Set the {@link JmsTemplate} to use.
	 */
	public void setJmsTemplate(JmsTemplate jmsTemplate) {
		this.jmsTemplate = jmsTemplate;
	}

	/**
	 * Set the {@link MessageConverter} to use to convert a {@link Message} from
	 * the messaging to and from a {@link javax.jms.Message}. By default, a
	 * {@link MessagingMessageConverter} is defined using a {@link SimpleMessageConverter}
	 * to convert the payload of the message.
	 * <p>Consider configuring a {@link MessagingMessageConverter} with a different
	 * {@link MessagingMessageConverter#setPayloadConverter(MessageConverter) payload converter}
	 * for more advanced scenario.
	 *
	 * @see org.springframework.jms.support.converter.MessagingMessageConverter
	 */
	public void setJmsMessageConverter(MessageConverter jmsMessageConverter) {
		this.jmsMessageConverter = jmsMessageConverter;
	}

	/**
	 * Configure the default destination name to use in send methods that don't have
	 * a destination argument. If a default destination is not configured, send methods
	 * without a destination argument will raise an exception if invoked.
	 * @see #setDefaultDestination(Object)
	 */
	public void setDefaultDestinationName(String defaultDestinationName) {
		this.defaultDestinationName = defaultDestinationName;
	}

	/**
	 * Return the configured default destination name.
	 */
	public String getDefaultDestinationName() {
		return this.defaultDestinationName;
	}

	@Override
	public void afterPropertiesSet() {
		Assert.notNull(this.jmsTemplate, "Property 'jmsTemplate' is required");
		Assert.notNull(this.jmsMessageConverter, "Property 'jmsMessageConverter' is required");
	}


	@Override
	public void send(Message<?> message) {
		Destination defaultDestination = getDefaultDestination();
		if (defaultDestination != null) {
			send(defaultDestination, message);
		}
		else {
			send(getRequiredDefaultDestinationName(), message);
		}
	}

	@Override
	public void convertAndSend(Object payload) throws MessagingException {
		convertAndSend(payload, null);
	}

	@Override
	public void convertAndSend(Object payload, MessagePostProcessor postProcessor) throws MessagingException {
		Destination defaultDestination = getDefaultDestination();
		if (defaultDestination != null) {
			convertAndSend(defaultDestination, payload, postProcessor);
		}
		else {
			convertAndSend(getRequiredDefaultDestinationName(), payload, postProcessor);
		}
	}

	@Override
	public void send(String destinationName, Message<?> message) throws MessagingException {
		doSend(destinationName, message);
	}

	@Override
	public void convertAndSend(String destinationName, Object payload) throws MessagingException {
		convertAndSend(destinationName, payload, (Map<String, Object>) null);
	}

	@Override
	public void convertAndSend(String destinationName, Object payload, Map<String, Object> headers)
			throws MessagingException {
		convertAndSend(destinationName, payload, headers, null);
	}

	@Override
	public void convertAndSend(String destinationName, Object payload, MessagePostProcessor postProcessor)
			throws MessagingException {
		convertAndSend(destinationName, payload, null, postProcessor);
	}

	@Override
	public void convertAndSend(String destinationName, Object payload, Map<String, Object> headers,
			MessagePostProcessor postProcessor) throws MessagingException {
		Message<?> message = doConvert(payload, headers, postProcessor);
		send(destinationName, message);
	}

	@Override
	public Message<?> receive() {
		Destination defaultDestination = getDefaultDestination();
		if (defaultDestination != null) {
			return receive(defaultDestination);
		}
		else {
			return receive(getRequiredDefaultDestinationName());
		}
	}

	@Override
	public <T> T receiveAndConvert(Class<T> targetClass) {
		Destination defaultDestination = getDefaultDestination();
		if (defaultDestination != null) {
			return receiveAndConvert(defaultDestination, targetClass);
		}
		else {
			return receiveAndConvert(getRequiredDefaultDestinationName(), targetClass);
		}
	}

	@Override
	public Message<?> receive(String destinationName) throws MessagingException {
		return doReceive(destinationName);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T receiveAndConvert(String destinationName, Class<T> targetClass) throws MessagingException {
		Message<?> message = doReceive(destinationName);
		if (message != null) {
			return (T) getMessageConverter().fromMessage(message, targetClass);
		}
		else {
			return null;
		}
	}

	@Override
	protected void doSend(Destination destination, Message<?> message) {
		jmsTemplate.send(destination, new MessagingMessageCreator(message, this.jmsMessageConverter));
	}

	protected void doSend(String destinationName, Message<?> message) {
		jmsTemplate.send(destinationName, new MessagingMessageCreator(message, this.jmsMessageConverter));
	}

	@Override
	protected Message<?> doReceive(Destination destination) {
		javax.jms.Message jmsMessage = jmsTemplate.receive(destination);
		return doConvert(jmsMessage);
	}

	protected Message<?> doReceive(String destinationName) {
		javax.jms.Message jmsMessage = jmsTemplate.receive(destinationName);
		return doConvert(jmsMessage);
	}

	protected String getRequiredDefaultDestinationName() {
		String name = getDefaultDestinationName();
		if (name == null) {
			throw new IllegalStateException(
					"No 'defaultDestination' or 'defaultDestinationName' specified. " +
							"Check configuration of JmsMessagingTemplate."
			);
		}
		return name;
	}

	protected Message<?> doConvert(javax.jms.Message message) {
		if (message == null) {
			return null;
		}
		try {
			return (Message<?>) jmsMessageConverter.fromMessage(message);
		}
		catch (JMSException e) {
			throw new MessageConversionException("Could not convert '" + message + "'", e);
		}
	}


	private static class MessagingMessageCreator implements MessageCreator {

		private final Message<?> message;

		private final MessageConverter messageConverter;

		private MessagingMessageCreator(Message<?> message, MessageConverter messageConverter) {
			this.message = message;
			this.messageConverter = messageConverter;
		}

		@Override
		public javax.jms.Message createMessage(Session session) throws JMSException {
			return messageConverter.toMessage(message, session);
		}
	}

}
