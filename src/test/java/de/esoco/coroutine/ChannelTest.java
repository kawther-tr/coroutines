//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// This file is a part of the 'coroutines' project.
// Copyright 2018 Elmar Sonnenschein, esoco GmbH, Flensburg, Germany
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//	  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
package de.esoco.coroutine;

import org.junit.BeforeClass;
import org.junit.Test;

import static de.esoco.coroutine.ChannelId.stringChannel;
import static de.esoco.coroutine.CoroutineScope.launch;
import static de.esoco.coroutine.Coroutines.EXCEPTION_HANDLER;
import static de.esoco.coroutine.step.ChannelReceive.receive;
import static de.esoco.coroutine.step.ChannelSend.send;
import static de.esoco.coroutine.step.CodeExecution.apply;
import static de.esoco.coroutine.step.Select.select;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static org.obrel.type.StandardTypes.NAME;


/********************************************************************
 * Test of {@link Coroutine}.
 *
 * @author eso
 */
public class ChannelTest
{
	//~ Static fields/initializers ---------------------------------------------

	private static final ChannelId<String> TEST_CHANNEL =
		stringChannel("TestChannel");

	private static final Coroutine<String, String> SEND =
		Coroutine.first(apply((String s) -> s + "test"))
				 .then(send(TEST_CHANNEL))
				 .with(NAME, "Send");

	private static final Coroutine<?, String> RECEIVE =
		Coroutine.first(receive(TEST_CHANNEL))
				 .with(NAME, "Receive")
				 .then(apply(s -> s.toUpperCase()));

	private static final ChannelId<String> CHANNEL_A = stringChannel("A");
	private static final ChannelId<String> CHANNEL_B = stringChannel("B");
	private static final ChannelId<String> CHANNEL_C = stringChannel("C");

	private static final Coroutine<Void, String> SELECT_ABC =
		Coroutine.first(
			select(receive(CHANNEL_A), receive(CHANNEL_B), receive(CHANNEL_C)));

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Test class setup.
	 */
	@BeforeClass
	public static void setup()
	{
		// suppress stacktraces from error testing
		Coroutines.getDefaultContext().set(EXCEPTION_HANDLER, t ->{});
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Test of asynchronous channel communication.
	 */
	@Test
	public void testChannel()
	{
		Coroutine<?, String> receive2 =
			RECEIVE.then(apply((String s) -> s.toLowerCase()));

		launch(
			run ->
			{
				Continuation<String> r1 = run.async(RECEIVE);
				Continuation<String> r2 = run.async(receive2);

				Continuation<?> s1 = run.async(SEND, "123");
				Continuation<?> s2 = run.async(SEND, "456");

				assertEquals("123test", s1.getResult());
				assertEquals("456test", s2.getResult());

				String r1v = r1.getResult();
				String r2v = r2.getResult();

				// because of the concurrent execution it is not fixed which
				// of the values r1 and r2 will receive
				assertTrue(
					"123test".equalsIgnoreCase(r1v) ||
					"456test".equalsIgnoreCase(r1v));
				assertTrue(
					"123test".equalsIgnoreCase(r2v) ||
					"456test".equalsIgnoreCase(r2v));
				assertTrue(s1.isFinished());
				assertTrue(s2.isFinished());
				assertTrue(r1.isFinished());
				assertTrue(r2.isFinished());
			});
	}

	/***************************************
	 * Test of channel closing.
	 */
	@Test
	public void testChannelClose()
	{
		launch(
			run ->
			{
				Continuation<String> r = null;

				try
				{
					r = run.async(RECEIVE);

					r.getChannel(TEST_CHANNEL).close();
					r.await();
					fail();
				}
				catch (ChannelClosedException e)
				{
					// expected
					r.errorHandled();
				}
			});

		launch(
			run ->
			{
				Continuation<String> r = null;

				try
				{
					run.getChannel(TEST_CHANNEL).close();
					r = run.async(SEND, "TEST");

					r.await();
					fail();
				}
				catch (ChannelClosedException e)
				{
					// expected
					r.errorHandled();
				}
			});
	}

	/***************************************
	 * Test of channel select.
	 */
	@Test
	public void testChannelSelect()
	{
		testSelect(CHANNEL_A);
		testSelect(CHANNEL_B);
		testSelect(CHANNEL_C);
	}

	/***************************************
	 * Test selecting a certain channel.
	 *
	 * @param rId The channel ID
	 */
	private void testSelect(ChannelId<String> rId)
	{
		launch(
			run ->
			{
				Continuation<String> c = run.async(SELECT_ABC);

				Channel<String> channel = run.context().getChannel(rId);

				channel.sendBlocking("TEST-" + rId);

				assertEquals("TEST-" + rId, c.getResult());
				assertTrue(c.isFinished());
			});
	}
}
