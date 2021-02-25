package com.github.blahblahbloopster.crypto

class DummyKeyList : KeyList, MutableSet<KeyHolder> by LinkedHashSet()
