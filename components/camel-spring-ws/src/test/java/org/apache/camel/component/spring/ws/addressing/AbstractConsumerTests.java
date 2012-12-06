/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.spring.ws.addressing;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.component.spring.ws.utils.OutputChannelReceiver;
import org.apache.camel.component.spring.ws.utils.TestUtil;
import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.fest.assertions.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.addressing.client.ActionCallback;
import org.springframework.ws.soap.addressing.core.EndpointReference;
import org.springframework.ws.soap.addressing.core.MessageAddressingProperties;
import org.springframework.ws.soap.addressing.version.Addressing10;
import org.springframework.ws.soap.client.SoapFaultClientException;

/**
 * Provides abstract test for fault and output params for spring-ws:to: and
 * spring-ws:action: endpoints
 * 
 * @author a.zachar
 */
public abstract class AbstractConsumerTests extends CamelSpringTestSupport {
    private final String xmlBody = "<GetQuote xmlns=\"http://www.webserviceX.NET/\"><symbol>GOOG</symbol></GetQuote>";

    private WebServiceTemplate webServiceTemplate;
    private OutputChannelReceiver response;
    private OutputChannelReceiver newReply;

    private StreamSource source;
    private StreamResult result;
    private String requestInputAction;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        // initialize beans for catching results
        webServiceTemplate = applicationContext.getBean("webServiceTemplate", WebServiceTemplate.class);
        newReply = getMandatoryBean(OutputChannelReceiver.class, "replyReceiver");
        response = getMandatoryBean(OutputChannelReceiver.class, "responseReceiver");

        // sample data
        source = new StreamSource(new StringReader(xmlBody));
        result = new StreamResult(new StringWriter());

        // reset from previous test
        response.clear();
        newReply.clear();
        requestInputAction = null;
    }

    @After
    public void after() throws Exception {
        assertNotNull(result);
    }

    /**
     * Creates WS-Addressing Action and ReplyTo param for request
     * 
     * @param action
     * @param replyTo
     * @return
     * @throws URISyntaxException
     */
    protected final ActionCallback actionAndReplyTo(String action, String replyTo) throws URISyntaxException {
        requestInputAction = action;
        ActionCallback requestCallback = new ActionCallback(action);
        if (replyTo != null) {
            requestCallback.setReplyTo(new EndpointReference(new URI(replyTo)));
        }
        return requestCallback;
    }

    /**
     * Creates WS-Addressing Action param for request
     * 
     * @param action
     * @param replyTo
     * @return
     * @throws URISyntaxException
     */
    protected final ActionCallback action(String action) throws URISyntaxException {
        return actionAndReplyTo(action, null);
    }

    /**
     * Creates WS-Addressing To and ReplyTo param for request
     * 
     * @param action
     * @param replyTo
     * @return
     * @throws URISyntaxException
     */
    protected final ActionCallback toAndReplyTo(String to, String replyTo) throws URISyntaxException {
        requestInputAction = "http://doesn-not-matter.com";
        ActionCallback requestCallback = new ActionCallback(new URI(requestInputAction), new Addressing10(), new URI(to));
        if (replyTo != null) {
            requestCallback.setReplyTo(new EndpointReference(new URI(replyTo)));
        }
        return requestCallback;
    }

    /**
     * Creates WS-Addressing To param for request
     * 
     * @param action
     * @param replyTo
     * @return
     * @throws URISyntaxException
     */
    protected final ActionCallback to(String to) throws URISyntaxException {
        return toAndReplyTo(to, null);
    }

    /**
     * Construct a default action for the response message from the input
     * message using the default response action suffix.
     * 
     * @return
     * @throws URISyntaxException
     */
    private URI getDefaultResponseAction() throws URISyntaxException {
        return new URI(requestInputAction + "Response");
    }

    /**
     * Only response is allow using a brand new channel
     * 
     * @return
     */

    protected final MessageAddressingProperties newChannelParams() {
        assertNotNull(newReply);
        assertNotNull(newReply.getMessageContext());
        SoapMessage request = (SoapMessage)newReply.getMessageContext().getRequest();
        assertNotNull(request);

        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties(request);
        assertNotNull(wsaProperties);
        assertNotNull(wsaProperties.getTo());
        return wsaProperties;
    }

    /**
     * Only response is allow using same channel
     * 
     * @return
     */
    protected final MessageAddressingProperties sameChannelParams() {
        // we expect the same channel reply
        assertNull(newReply.getMessageContext());

        assertNotNull(response);
        assertNotNull(response.getMessageContext());

        SoapMessage soapResponse = (SoapMessage)response.getMessageContext().getResponse();
        assertNotNull(soapResponse);

        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties(soapResponse);
        assertNotNull(wsaProperties);
        return wsaProperties;
    }

    /**
     * Provides such an ActionCallback that sets the WS-Addressing param replyTo
     * or doesn't set WS-Addressing param replyTo. In other words it cause
     * response to be return using new or same channel as the request.
     * 
     * @param action
     * @return
     * @throws URISyntaxException
     */
    abstract ActionCallback channelIn(String action) throws URISyntaxException;

    /**
     * Provide corresponding results based on channel input. These two abstract methods (channelIn and
     * channelOut)are bind together tighly.
     * 
     * @return
     */
    abstract MessageAddressingProperties channelOut();

    @Test
    public void defaultAction4ouput() throws Exception {
        ActionCallback requestCallback = channelIn("http://default-ok.com/");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);

        Assertions.assertThat(channelOut().getAction()).isEqualTo(getDefaultResponseAction());
    }

    @Test
    public void defaultAction4fault() throws Exception {
        ActionCallback requestCallback = channelIn("http://default-fault.com/");
        try {
            webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        } catch (SoapFaultClientException e) {
            // ok - cause fault response
        }
        Assertions.assertThat(channelOut().getAction()).isEqualTo(getDefaultResponseAction());
    }

    @Test
    public void customAction4output() throws Exception {
        ActionCallback requestCallback = channelIn("http://uri-ok.com");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);

        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://customURIOutputAction"));
    }

    @Test
    public void customAction4fault() throws Exception {
        ActionCallback requestCallback = channelIn("http://uri-fault.com");
        try {
            webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        } catch (SoapFaultClientException e) {
            // ok - cause fault response
        }
        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://customURIFaultAction"));
    }

    @Test
    @Ignore(value = "Not implemented yet")
    public void overrideHeaderAction4output() throws Exception {
        ActionCallback requestCallback = channelIn("http://override-ok.com");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);

        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://outputHeader.com"));
    }

    @Test
    @Ignore(value = "Not implemented yet")
    public void overrideHeaderAction4fault() throws Exception {
        ActionCallback requestCallback = channelIn("http://override-fault.com");
        try {
            webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        } catch (SoapFaultClientException e) {
            // ok - cause fault response
        }
        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://faultHeader.com"));
    }

    @Test
    @Ignore(value = "Not implemented yet")
    public void headerAction4output() throws Exception {
        ActionCallback requestCallback = channelIn("http://headerOnly-ok.com");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);

        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://outputHeader.com"));
    }

    @Test
    @Ignore(value = "Not implemented yet")
    public void headerAction4fault() throws Exception {
        ActionCallback requestCallback = channelIn("http://headerOnly-fault.com");
        try {
            webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        } catch (SoapFaultClientException e) {
            // ok - cause fault response
        }
        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://faultHeader.com"));
    }

    @Test
    public void onlyCustomOutputSpecified4output() throws Exception {
        ActionCallback requestCallback = channelIn("http://uriOutputOnly-ok.com/");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);

        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://customURIOutputAction"));
    }

    @Test
    public void onlyCustomOutputSpecified4fault() throws Exception {
        ActionCallback requestCallback = channelIn("http://uriOutputOnly-fault.com/");
        try {
            webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        } catch (SoapFaultClientException e) {
            // ok - cause fault response
        }
        Assertions.assertThat(channelOut().getAction()).isEqualTo(getDefaultResponseAction());
    }

    @Test
    public void onlyCustomFaultSpecified4output() throws Exception {
        ActionCallback requestCallback = channelIn("http://uriFaultOnly-ok.com/");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);

        Assertions.assertThat(channelOut().getAction()).isEqualTo(getDefaultResponseAction());
    }

    @Test
    public void onlyCustomFaultSpecified4fault() throws Exception {
        ActionCallback requestCallback = channelIn("http://uriFaultOnly-fault.com/");
        try {
            webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        } catch (SoapFaultClientException e) {
            // ok - cause fault response
        }
        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://customURIFaultAction"));
    }

}
