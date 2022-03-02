package com.nurkiewicz.reactor;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
Ważny operator który zamienia pusty strumień w niepusty.
Przydatny w sytuacji gdy gdzieś pojawi się puste mono. Normalnie byłoby ono przepropagowane, ale nie odpalą się inne
operatory. Może to skutkować pustą stroną.
 */

public class R048_SwitchIfEmpty {

	private static final Logger log = LoggerFactory.getLogger(R048_SwitchIfEmpty.class);

	@Test
	public void test_13() {

	}

	@Test
	public void test_then() {

	}

}
