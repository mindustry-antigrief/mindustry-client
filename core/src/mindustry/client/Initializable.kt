package mindustry.client

interface Initializable {

    /** Run once on launch.  [arc.Core] values will not be null. */
    fun initializeAlways() {}

    /** Run on [mindustry.game.EventType.ClientLoadEvent]. */
    fun initializeGameLoad() {}

    /** Run on launch for headless applications. */
    fun initializeHeadless() {}

    /** Run once per tick. */
    fun update() {}
}
