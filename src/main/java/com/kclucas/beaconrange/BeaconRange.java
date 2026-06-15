package com.kclucas.beaconrange;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeaconRange implements ModInitializer {
	public static final String MOD_ID = "beaconrange";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {


		LOGGER.info("Beacon Range by KCLucas");
	}
}