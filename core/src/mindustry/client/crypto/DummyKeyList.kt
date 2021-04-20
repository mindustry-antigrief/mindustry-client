package mindustry.client.crypto

class DummyKeyList : KeyList, MutableSet<KeyHolder> by LinkedHashSet()
