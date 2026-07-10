export interface ActivityEventDto {
  userId: string;
  displayName?: string;
  trackId: string;
  title: string;
  artist: string;
  album?: string;
  artworkUrl?: string;
  sourceLabel?: string;
  startedAtMs: number;
  positionMs: number;
  durationMs: number;
}

export interface ActivityFeedDto {
  events: ActivityEventDto[];
  updatedAtMs: number;
}

export interface FriendsListDto {
  friendIds: string[];
}

export interface AddFriendBody {
  friendId: string;
}

export interface LibrarySnapshotTrackDto {
  vantaTrackId: string;
  title: string;
  artist: string;
  album?: string;
  isrc?: string;
  isFavorite?: boolean;
  playCount?: number;
  lastPlayedAtMs?: number;
  addedAtMs?: number;
  artworkUrl?: string;
  sourceProviderIds?: string[];
}

export interface LibrarySnapshotDto {
  vantaUserId: string;
  deviceName: string;
  version?: number;
  generatedAtMs: number;
  tracks?: LibrarySnapshotTrackDto[];
  likedTrackIds?: string[];
  recentPlayedTrackIds?: string[];
}

export interface SyncPushResponseDto {
  snapshotId?: string;
  serverTracksMerged: number;
  conflicts: number;
  nextSyncAtMs?: number;
}
