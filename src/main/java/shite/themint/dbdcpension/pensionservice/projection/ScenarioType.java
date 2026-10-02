package shite.themint.dbdcpension.pensionservice.projection;

/** The three economic scenarios of the regulated pension projection. The front end turns the codes into text. */
public enum ScenarioType {
	/** "Expected from investments": the expected pension if nothing else changes. */
	EXPECTED,
	/** "If the economy is experiencing unfavourable times". */
	UNFAVOURABLE,
	/** "If the economy is experiencing favourable times". */
	FAVOURABLE
}
