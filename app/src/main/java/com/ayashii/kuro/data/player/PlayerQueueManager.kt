package com.ayashii.kuro.data.player

import androidx.media3.common.Player
import com.ayashii.kuro.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Random

interface IPlayerQueueManager {
    val queueState: StateFlow<List<Track>>
    val currentIndex: StateFlow<Int>
    val currentTrack: StateFlow<Track?>
    val repeatMode: StateFlow<Int>
    val isShuffle: StateFlow<Boolean>
    val consecutiveErrors: StateFlow<Int>

    fun setQueue(tracks: List<Track>, startIndex: Int = 0)
    fun playTrack(track: Track, newQueue: List<Track> = emptyList())
    fun playTrackAtIndex(index: Int)
    fun addToQueue(track: Track)
    fun removeFromQueue(index: Int): Boolean
    fun moveQueueItem(fromIndex: Int, toIndex: Int): Boolean
    fun clearUpcomingQueue()
    fun getNextTrack(userInitiated: Boolean, isErrorRecovery: Boolean = false): Track?
    fun getPreviousTrack(): Track?
    fun hasNext(): Boolean
    fun hasPrevious(): Boolean
    fun toggleShuffle()
    fun toggleRepeat()
    fun recordPlaybackSuccess()
    fun recordPlaybackFailure(): Int
    fun resetErrorCount()
}

object PlayerQueueManager : IPlayerQueueManager {

    private val lock = Any()
    private val queue = ArrayList<Track>()
    private val playbackHistory = ArrayDeque<Int>()
    private val shuffleDeck = ArrayList<Int>()
    private var lastAutoAdvanceTimestamp = 0L

    private val _queueState = MutableStateFlow<List<Track>>(emptyList())
    override val queueState: StateFlow<List<Track>> = _queueState.asStateFlow()

    private val _currentIndex = MutableStateFlow<Int>(-1)
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    override val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _repeatMode = MutableStateFlow<Int>(Player.REPEAT_MODE_OFF)
    override val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow<Boolean>(false)
    override val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _consecutiveErrors = MutableStateFlow<Int>(0)
    override val consecutiveErrors: StateFlow<Int> = _consecutiveErrors.asStateFlow()

    fun getInstance(): PlayerQueueManager = this

    override fun setQueue(tracks: List<Track>, startIndex: Int) {
        synchronized(lock) {
            queue.clear()
            queue.addAll(tracks)
            playbackHistory.clear()
            _consecutiveErrors.value = 0
            if (queue.isEmpty()) {
                _currentIndex.value = -1
                _currentTrack.value = null
                shuffleDeck.clear()
            } else {
                val validIndex = startIndex.coerceIn(0, queue.size - 1)
                _currentIndex.value = validIndex
                _currentTrack.value = queue[validIndex]
                shuffleDeck.clear()
                if (_isShuffle.value) {
                    shuffleDeck.addAll(generateFisherYatesDeck(queue.size, validIndex))
                }
            }
            _queueState.value = queue.toList()
        }
    }

    override fun playTrack(track: Track, newQueue: List<Track>) {
        synchronized(lock) {
            _consecutiveErrors.value = 0
            if (newQueue.isNotEmpty() && newQueue != queue) {
                queue.clear()
                queue.addAll(newQueue)
                playbackHistory.clear()
                val idx = queue.indexOfFirst { it.id == track.id }
                if (idx != -1) {
                    _currentIndex.value = idx
                    _currentTrack.value = queue[idx]
                } else {
                    queue.add(track)
                    _currentIndex.value = queue.size - 1
                    _currentTrack.value = track
                }
                shuffleDeck.clear()
                if (_isShuffle.value) {
                    shuffleDeck.addAll(generateFisherYatesDeck(queue.size, _currentIndex.value))
                }
            } else {
                val idx = queue.indexOfFirst { it.id == track.id }
                if (idx != -1) {
                    if (_currentIndex.value in queue.indices && _currentIndex.value != idx) {
                        playbackHistory.addLast(_currentIndex.value)
                        if (playbackHistory.size > 200) playbackHistory.removeFirst()
                    }
                    _currentIndex.value = idx
                    _currentTrack.value = queue[idx]
                    shuffleDeck.remove(idx)
                } else if (queue.isEmpty()) {
                    queue.add(track)
                    _currentIndex.value = 0
                    _currentTrack.value = track
                    shuffleDeck.clear()
                } else {
                    val insertIdx = (_currentIndex.value + 1).coerceIn(0, queue.size)
                    queue.add(insertIdx, track)
                    if (_currentIndex.value in queue.indices) {
                        playbackHistory.addLast(_currentIndex.value)
                        if (playbackHistory.size > 200) playbackHistory.removeFirst()
                    }
                    _currentIndex.value = insertIdx
                    _currentTrack.value = track
                    if (_isShuffle.value) {
                        shuffleDeck.clear()
                        shuffleDeck.addAll(generateFisherYatesDeck(queue.size, insertIdx))
                    }
                }
            }
            _queueState.value = queue.toList()
        }
    }

    override fun playTrackAtIndex(index: Int) {
        synchronized(lock) {
            if (index !in queue.indices) return
            if (_currentIndex.value in queue.indices && _currentIndex.value != index) {
                playbackHistory.addLast(_currentIndex.value)
                if (playbackHistory.size > 200) playbackHistory.removeFirst()
            }
            _currentIndex.value = index
            _currentTrack.value = queue[index]
            shuffleDeck.remove(index)
            _consecutiveErrors.value = 0
        }
    }

    override fun addToQueue(track: Track) {
        synchronized(lock) {
            queue.add(track)
            val newIndex = queue.size - 1
            if (_queueState.value.isEmpty() || _currentIndex.value == -1) {
                _currentIndex.value = 0
                _currentTrack.value = track
            } else if (_isShuffle.value) {
                if (shuffleDeck.isEmpty()) {
                    shuffleDeck.add(newIndex)
                } else {
                    val randomPos = Random().nextInt(shuffleDeck.size + 1)
                    shuffleDeck.add(randomPos, newIndex)
                }
            }
            _queueState.value = queue.toList()
        }
    }

    override fun removeFromQueue(index: Int): Boolean {
        synchronized(lock) {
            if (index !in queue.indices) return false
            queue.removeAt(index)
            val current = _currentIndex.value
            when {
                index < current -> {
                    _currentIndex.value = current - 1
                }
                index == current -> {
                    if (queue.isEmpty()) {
                        _currentIndex.value = -1
                        _currentTrack.value = null
                    } else {
                        val newIdx = if (current >= queue.size) queue.size - 1 else current
                        _currentIndex.value = newIdx
                        _currentTrack.value = queue[newIdx]
                    }
                }
            }
            val remappedHistory = playbackHistory.filter { it != index }.map { if (it > index) it - 1 else it }
            playbackHistory.clear()
            playbackHistory.addAll(remappedHistory)

            val remappedDeck = shuffleDeck.filter { it != index }.map { if (it > index) it - 1 else it }
            shuffleDeck.clear()
            shuffleDeck.addAll(remappedDeck)

            _queueState.value = queue.toList()
            return true
        }
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int): Boolean {
        synchronized(lock) {
            if (fromIndex !in queue.indices || toIndex !in queue.indices) return false
            if (fromIndex == toIndex) return true
            val item = queue.removeAt(fromIndex)
            queue.add(toIndex, item)

            val current = _currentIndex.value
            _currentIndex.value = remapIndex(current, fromIndex, toIndex)
            if (_currentIndex.value in queue.indices) {
                _currentTrack.value = queue[_currentIndex.value]
            }

            val remappedHistory = playbackHistory.map { remapIndex(it, fromIndex, toIndex) }
            playbackHistory.clear()
            playbackHistory.addAll(remappedHistory)

            val remappedDeck = shuffleDeck.map { remapIndex(it, fromIndex, toIndex) }
            shuffleDeck.clear()
            shuffleDeck.addAll(remappedDeck)

            _queueState.value = queue.toList()
            return true
        }
    }

    override fun clearUpcomingQueue() {
        synchronized(lock) {
            val current = _currentIndex.value
            if (current in queue.indices) {
                val retainCount = current + 1
                while (queue.size > retainCount) {
                    queue.removeAt(queue.size - 1)
                }
                shuffleDeck.removeAll { it >= retainCount }
                _queueState.value = queue.toList()
            }
        }
    }

    override fun getNextTrack(userInitiated: Boolean, isErrorRecovery: Boolean): Track? {
        synchronized(lock) {
            if (queue.isEmpty()) return null

            if (!userInitiated) {
                val now = System.currentTimeMillis()
                if (now - lastAutoAdvanceTimestamp < 900L) {
                    return _currentTrack.value ?: queue.getOrNull(_currentIndex.value)
                }
                lastAutoAdvanceTimestamp = now
            }

            if (_repeatMode.value == Player.REPEAT_MODE_ONE && !userInitiated && !isErrorRecovery) {
                return _currentTrack.value ?: queue.getOrNull(_currentIndex.value)
            }

            val nextIndex: Int
            if (_isShuffle.value) {
                if (shuffleDeck.isNotEmpty()) {
                    nextIndex = shuffleDeck.removeAt(0)
                } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && queue.size > 1) {
                    val newDeck = generateFisherYatesDeck(queue.size, _currentIndex.value)
                    shuffleDeck.clear()
                    shuffleDeck.addAll(newDeck)
                    nextIndex = if (shuffleDeck.isNotEmpty()) shuffleDeck.removeAt(0) else -1
                } else {
                    nextIndex = -1
                }
            } else {
                if (_currentIndex.value + 1 < queue.size) {
                    nextIndex = _currentIndex.value + 1
                } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && queue.isNotEmpty()) {
                    nextIndex = 0
                } else {
                    nextIndex = -1
                }
            }

            return if (nextIndex in queue.indices) {
                if (_currentIndex.value in queue.indices) {
                    playbackHistory.addLast(_currentIndex.value)
                    if (playbackHistory.size > 200) playbackHistory.removeFirst()
                }
                _currentIndex.value = nextIndex
                val track = queue[nextIndex]
                _currentTrack.value = track
                track
            } else {
                null
            }
        }
    }

    override fun getPreviousTrack(): Track? {
        synchronized(lock) {
            if (queue.isEmpty()) return null

            if (playbackHistory.isNotEmpty()) {
                val prevIndex = playbackHistory.removeLast()
                if (prevIndex in queue.indices) {
                    if (_isShuffle.value && _currentIndex.value in queue.indices) {
                        shuffleDeck.add(0, _currentIndex.value)
                    }
                    _currentIndex.value = prevIndex
                    val track = queue[prevIndex]
                    _currentTrack.value = track
                    return track
                }
            }

            val prevIndex = if (!_isShuffle.value) {
                if (_currentIndex.value - 1 >= 0) {
                    _currentIndex.value - 1
                } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && queue.isNotEmpty()) {
                    queue.size - 1
                } else {
                    -1
                }
            } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && queue.isNotEmpty()) {
                queue.size - 1
            } else {
                -1
            }

            return if (prevIndex in queue.indices) {
                _currentIndex.value = prevIndex
                val track = queue[prevIndex]
                _currentTrack.value = track
                track
            } else {
                null
            }
        }
    }

    override fun hasNext(): Boolean {
        synchronized(lock) {
            if (queue.isEmpty()) return false
            if (_repeatMode.value == Player.REPEAT_MODE_ALL || _repeatMode.value == Player.REPEAT_MODE_ONE) return true
            if (_isShuffle.value) {
                return shuffleDeck.isNotEmpty()
            }
            return _currentIndex.value + 1 < queue.size
        }
    }

    override fun hasPrevious(): Boolean {
        synchronized(lock) {
            if (queue.isEmpty()) return false
            if (playbackHistory.isNotEmpty()) return true
            if (_repeatMode.value == Player.REPEAT_MODE_ALL && queue.size > 1) return true
            if (!_isShuffle.value && _currentIndex.value > 0) return true
            return false
        }
    }

    override fun toggleShuffle() {
        synchronized(lock) {
            val newShuffle = !_isShuffle.value
            _isShuffle.value = newShuffle
            shuffleDeck.clear()
            if (newShuffle && queue.isNotEmpty()) {
                shuffleDeck.addAll(generateFisherYatesDeck(queue.size, _currentIndex.value))
            }
        }
    }

    override fun toggleRepeat() {
        synchronized(lock) {
            _repeatMode.value = when (_repeatMode.value) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    override fun recordPlaybackSuccess() {
        synchronized(lock) {
            _consecutiveErrors.value = 0
        }
    }

    override fun recordPlaybackFailure(): Int {
        synchronized(lock) {
            val current = _consecutiveErrors.value + 1
            _consecutiveErrors.value = current
            return current
        }
    }

    override fun resetErrorCount() {
        synchronized(lock) {
            _consecutiveErrors.value = 0
        }
    }

    fun updateCurrentTrack(track: Track) {
        synchronized(lock) {
            _currentTrack.value = track
            val idx = _currentIndex.value
            if (idx in queue.indices) {
                queue[idx] = track
                _queueState.value = queue.toList()
            }
        }
    }

    fun setRepeatMode(mode: Int) {
        synchronized(lock) {
            _repeatMode.value = mode
        }
    }

    fun setShuffle(shuffle: Boolean) {
        synchronized(lock) {
            if (_isShuffle.value != shuffle) {
                _isShuffle.value = shuffle
                shuffleDeck.clear()
                if (shuffle && queue.isNotEmpty()) {
                    shuffleDeck.addAll(generateFisherYatesDeck(queue.size, _currentIndex.value))
                }
            }
        }
    }

    private fun generateFisherYatesDeck(size: Int, excludeIndex: Int): List<Int> {
        if (size <= 1) return emptyList()
        val deck = ArrayList<Int>(size - 1)
        for (i in 0 until size) {
            if (i != excludeIndex) {
                deck.add(i)
            }
        }
        val rng = Random()
        for (i in deck.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val temp = deck[i]
            deck[i] = deck[j]
            deck[j] = temp
        }
        return deck
    }

    private fun remapIndex(index: Int, from: Int, to: Int): Int {
        return when {
            index == from -> to
            from < to && index in (from + 1)..to -> index - 1
            from > to && index in to until from -> index + 1
            else -> index
        }
    }
}
