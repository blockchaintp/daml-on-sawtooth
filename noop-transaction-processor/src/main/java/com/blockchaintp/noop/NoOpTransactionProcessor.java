package com.blockchaintp.noop;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Option.Builder;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import sawtooth.sdk.processor.TransactionProcessor;;

public class NoOpTransactionProcessor {

	public static CommandLine parseArgs(String[] args) throws ParseException {
		Options options = new Options();
		Option validator_option = Option.builder("c").required(true).longOpt("connect").hasArg().type(String.class)
				.argName("validator-address")
				.desc("Address and port to connect to the validator, e.g. tcp://localhost:4004").build();
		Option familyName_option = Option.builder("n").required(true).longOpt("family-name").hasArg().type(String.class)
				.argName("family-name").desc("Family Name which this TP will handle").build();
		Option familyVersion_option = Option.builder("f").required().longOpt("family-version").hasArg()
				.type(String.class).argName("family-version").desc("Version of the family this TP will handle").build();
		Option strategy_option = Option.builder("s").required(false).longOpt("strategy").argName("noop-strategy")
				.type(Integer.class)
				.desc("1=Accept All transactions, 2=Reject all transactions, 3=InternalError all transactions").build();
		options.addOption(validator_option).addOption(familyName_option).addOption(familyVersion_option)
				.addOption(strategy_option);
		CommandLineParser parser = new DefaultParser();
		return parser.parse(options, args);
	}

	@SuppressWarnings("unused")
	public static void main(String[] args) {
		try {
			CommandLine cli = parseArgs(args);
			String validatorAddress=cli.getOptionValue("validator-address");
			TransactionProcessor processor=new TransactionProcessor(validatorAddress);
			
			String namespace=cli.getOptionValue("family-namespace","noop");
			String version=cli.getOptionValue("family-version","1.0");
			
			Integer strategy=(Integer) cli.getParsedOptionValue("noop-strategy");
			if ( strategy > 3 || strategy < 1) {
				throw new RuntimeException ( "Strategy must be 1, 2, or 3");
			}
					
			
			NoOpTransactionHandler transactionHandler = new NoOpTransactionHandler(namespace, version, strategy);
			processor.addHandler(transactionHandler);
			Thread t=new Thread(processor);
			t.start();
					
			
		} catch (ParseException e) {
			e.printStackTrace();
		}

	}

}
