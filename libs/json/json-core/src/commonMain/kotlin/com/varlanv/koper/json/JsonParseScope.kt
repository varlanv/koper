package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.DataSize

class JsonParseScope(bufferSize: DataSize) {
    init {
        require(bufferSize.bytes > 0)
    }

    var position = 0
    var limit = 0
    internal val stringScanner = JsonStringScanner(true)
    internal val buffer = bufferSize.allocate()
}
