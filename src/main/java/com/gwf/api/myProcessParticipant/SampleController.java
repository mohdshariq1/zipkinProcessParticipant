/*
 * Copyright 2013-2015 the original author or authors.
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

package com.gwf.api.myProcessParticipant;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAccessor;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.gwf.api.myApi.PersonDTO;
import com.gwf.api.myApi.PersonRepository;
import com.gwf.api.myApi.PersonService;

/**
 * @author Spencer Gibb
 */
@RestController
public class SampleController implements ApplicationListener<EmbeddedServletContainerInitializedEvent> {

	private static final Log log = LogFactory.getLog(SampleController.class);

	@Autowired
	private RestTemplate restTemplate;
	@Autowired
	private Tracer tracer;
	@Autowired
	private SpanAccessor accessor;
	@Autowired
	private SampleBackground controller;
	@Autowired
	private Random random;
	private int port;

	@Autowired
	private PersonService personService;

	@Autowired
	private PersonRepository personRepository;

	@RequestMapping("/")
	public String hi() throws InterruptedException {
		Thread.sleep(this.random.nextInt(1000));
		log.info("Home page");
		String s = this.restTemplate.getForObject("http://localhost:" + this.port + "/hi2", String.class);
		return "hi/" + s;
	}

	@RequestMapping("/call")
	public Callable<String> call() {
		return new Callable<String>() {
			@Override
			public String call() throws Exception {
				int millis = SampleController.this.random.nextInt(1000);
				Thread.sleep(millis);
				SampleController.this.tracer.addTag("callable-sleep-millis", String.valueOf(millis));
				Span currentSpan = SampleController.this.accessor.getCurrentSpan();

				String s = restTemplate.getForObject("http://localhost:" + port + "/hi2", String.class);

				return "async hi: " + currentSpan + s;
			}	
		};
	}

	@RequestMapping("/async")
	public String async() throws InterruptedException {
		log.info("async");
		this.controller.background();
		return "ho";
	}

	@RequestMapping("/hi2")
	public String hi2() throws InterruptedException {
		log.info("hi2");
		int millis = this.random.nextInt(1000);
		Thread.sleep(millis);
		this.tracer.addTag("random-sleep-millis", String.valueOf(millis));
		
		//String s = restTemplate.getForObject("http://localhost:8070"  + "/hi2", String.class);

		return "hi2";
	}

	@RequestMapping("/traced")
	public String traced() throws InterruptedException {
		Span span = this.tracer.createSpan("http:customTraceEndpoint", new AlwaysSampler());
		int millis = this.random.nextInt(1000);
		log.info(String.format("Sleeping    for [%d] millis", millis));
		Thread.sleep(millis);
		this.tracer.addTag("random-sleep-millis", String.valueOf(millis));

		String s = this.restTemplate.getForObject("http://localhost:8040" + "/call", String.class);
		this.tracer.close(span);
		return "traced/" + s;
	}

	@RequestMapping(value = "/persistPerson", method = RequestMethod.POST)
	public ResponseEntity<String> persistPerson(@RequestBody PersonDTO person) {
		if (personService.isValid(person)) {
			personRepository.persist(person);
			System.out.println(".......1");
			// ResponseEntity<String> response1 =
			// restTemplate.getForEntity("http://localhost:3380/hello",
			// String.class);
			// System.out.println(".......2" + response1.toString());

			// ResponseEntity<String> respons0 =
			// restTemplate.getForEntity("http://localhost:3380/chaining",
			// String.class);
			System.out.println(".......4");

			PersonDTO userModel = new PersonDTO("Mohd", "Shariq", new java.util.Date(), "Software Developer",
					java.math.BigDecimal.ONE);
			System.out.println(".......5");

			// RestTemplate restTemplate = new RestTemplate();
			List<HttpMessageConverter<?>> list = new ArrayList<HttpMessageConverter<?>>();
			list.add(new MappingJackson2HttpMessageConverter());
			restTemplate.setMessageConverters(list);
			System.out.println(".......6");
/*			ResponseEntity<PersonDTO> response = restTemplate.postForEntity("http://localhost:8060/createPerson",
					userModel, PersonDTO.class);
			System.out.println(response.getBody());
*/			System.out.println(".......7");

			return ResponseEntity.status(HttpStatus.CREATED).build();
		}
		return ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT).build();
	}

	@RequestMapping(value = "/availability", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public Boolean isUsernameAvailable(@RequestBody String username) {

		// a load of business logic that returns a boolean
		System.out.println("Is username available " + username);
		return Boolean.TRUE;
	}

	@RequestMapping(value = "/createPerson", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	public PersonDTO createUser(@RequestBody PersonDTO userModel) {

		// a load of business logic that validates the user model

		// RestTemplate restTemplate = new RestTemplate();
		// restTemplate.setMessageConverters(Arrays.asList(new
		// MappingJackson2HttpMessageConverter()));
		ResponseEntity<Boolean> response = restTemplate.postForEntity("http://localhost:8080/availability",
				userModel.getFirstName(), Boolean.class);
		System.out.println(response.getBody());

		// a load more business logic

		return userModel;
	}

	/*
	 * 
	 * 
	 * @RequestMapping("/traced2") public String traced2() throws
	 * InterruptedException { Span span =
	 * this.tracer.createSpan("http:customTraceEndpoint", new AlwaysSampler());
	 * int millis = this.random.nextInt(1000);
	 * log.info(String.format("Sleeping for [%d] millis", millis));
	 * Thread.sleep(millis); this.tracer.addTag("random-sleep-millis",
	 * String.valueOf(millis));
	 * 
	 * String s = this.restTemplate.getForObject("http://localhost:" + this.port
	 * + "/call", String.class); // this.tracer.close(span); return "traced2/" +
	 * s; }
	 * 
	 * @RequestMapping("/start") public String start() throws
	 * InterruptedException { int millis = this.random.nextInt(1000);
	 * log.info(String.format("Sleeping for [%d] millis", millis));
	 * Thread.sleep(millis); this.tracer.addTag("random-sleep-millis",
	 * String.valueOf(millis));
	 * 
	 * String s = this.restTemplate.getForObject("http://localhost:" + this.port
	 * + "/call", String.class); return "start/" + s; }
	 */
	@Override
	public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
		this.port = event.getEmbeddedServletContainer().getPort();
	}
}
