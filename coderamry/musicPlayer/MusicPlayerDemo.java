package LLD2.coderamry.musicPlayer;

import java.util.*;

// =====================
// ENUMS
// =====================
enum PlaybackState {
    STOPPED, PLAYING, PAUSED
}

enum AudioFormat {
    MP3, WAV, FLAC
}

// =====================
// ENTITIES
// =====================
class Song {
    String songId;
    String title;
    String artist;
    AudioFormat format;
    double duration; // in seconds

    Song(String songId, String title, String artist, AudioFormat format, double duration) {
        this.songId = songId;
        this.title = title;
        this.artist = artist;
        this.format = format;
        this.duration = duration;
    }
}

class Playlist {
    String playlistId;
    String name;
    List<Song> songs = new ArrayList<>();

    Playlist(String playlistId, String name) {
        this.playlistId = playlistId;
        this.name = name;
    }

    void addSong(Song song) { songs.add(song); }
    void removeSong(Song song) { songs.remove(song); }
}

// =====================
// REPOSITORY
// =====================
class SongRepository {
    Map<String, Song> songs = new HashMap<>();

    void addSong(Song s) { songs.put(s.songId, s); }
    Song findById(String id) { return songs.get(id); }
    List<Song> findAll() { return new ArrayList<>(songs.values()); }
}

class PlaylistRepository {
    Map<String, Playlist> playlists = new HashMap<>();

    void addPlaylist(Playlist p) { playlists.put(p.playlistId, p); }
    Playlist findById(String id) { return playlists.get(id); }
    List<Playlist> findAll() { return new ArrayList<>(playlists.values()); }
}

// =====================
// STRATEGY PATTERN FOR PLAYBACK
// =====================
interface PlaybackStrategy {
    void play(Song song);
}

class NormalPlayback implements PlaybackStrategy {
    public void play(Song song) {
        System.out.println("Playing song normally: " + song.title);
    }
}

class ShufflePlayback implements PlaybackStrategy {
    public void play(Song song) {
        System.out.println("Playing song in shuffle mode: " + song.title);
    }
}

// =====================
// OBSERVER PATTERN FOR NOTIFICATIONS
// =====================
interface PlayerObserver {
    void update(String message);
}

class UIObserver implements PlayerObserver {
    public void update(String message) {
        System.out.println("UI Update: " + message);
    }
}

class LoggerObserver implements PlayerObserver {
    public void update(String message) {
        System.out.println("Log: " + message);
    }
}

// =====================
// AUDIO ENGINE SERVICE
// =====================
class AudioEngine {
    PlaybackState state = PlaybackState.STOPPED;
    PlaybackStrategy strategy;
    List<PlayerObserver> observers = new ArrayList<>();

    AudioEngine(PlaybackStrategy strategy) { this.strategy = strategy; }

    void registerObserver(PlayerObserver obs) { observers.add(obs); }
    void notifyObservers(String message) {
        for(PlayerObserver obs : observers) obs.update(message);
    }

    void play(Song song) {
        strategy.play(song);
        state = PlaybackState.PLAYING;
        notifyObservers("Playing: " + song.title);
    }

    void pause() {
        if(state == PlaybackState.PLAYING) {
            state = PlaybackState.PAUSED;
            notifyObservers("Paused");
        }
    }

    void stop() {
        state = PlaybackState.STOPPED;
        notifyObservers("Stopped");
    }

    void setPlaybackStrategy(PlaybackStrategy strat) { this.strategy = strat; }
}

// =====================
// MUSIC PLAYER SERVICE
// =====================
class MusicPlayer {
    SongRepository songRepo;
    PlaylistRepository playlistRepo;
    AudioEngine engine;

    MusicPlayer(SongRepository sRepo, PlaylistRepository pRepo, AudioEngine engine) {
        this.songRepo = sRepo;
        this.playlistRepo = pRepo;
        this.engine = engine;
    }

    void createPlaylist(String id, String name) {
        Playlist p = new Playlist(id, name);
        playlistRepo.addPlaylist(p);
    }

    void addSongToPlaylist(String playlistId, String songId) {
        Playlist p = playlistRepo.findById(playlistId);
        Song s = songRepo.findById(songId);
        if(p != null && s != null) p.addSong(s);
    }

    void removeSongFromPlaylist(String playlistId, String songId) {
        Playlist p = playlistRepo.findById(playlistId);
        Song s = songRepo.findById(songId);
        if(p != null && s != null) p.removeSong(s);
    }

    void playSong(String songId) {
        Song s = songRepo.findById(songId);
        if(s != null) engine.play(s);
    }

    void pause() { engine.pause(); }
    void stop() { engine.stop(); }
}

// =====================
// DEMO
// =====================
public class MusicPlayerDemo {
    public static void main(String[] args) {
        SongRepository sRepo = new SongRepository();
        PlaylistRepository pRepo = new PlaylistRepository();

        Song s1 = new Song("S1", "Song A", "Artist 1", AudioFormat.MP3, 210);
        Song s2 = new Song("S2", "Song B", "Artist 2", AudioFormat.FLAC, 180);
        sRepo.addSong(s1); sRepo.addSong(s2);

        AudioEngine engine = new AudioEngine(new NormalPlayback());
        engine.registerObserver(new UIObserver());
        engine.registerObserver(new LoggerObserver());

        MusicPlayer player = new MusicPlayer(sRepo, pRepo, engine);
        player.createPlaylist("P1", "My Favorites");
        player.addSongToPlaylist("P1", "S1");
        player.addSongToPlaylist("P1", "S2");

        player.playSong("S1");
        player.pause();
        engine.setPlaybackStrategy(new ShufflePlayback());
        player.playSong("S2");
        player.stop();
    }
}
