package com.orange.ussd.registration.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.orange.ussd.registration.data.database.Converters;
import com.orange.ussd.registration.data.model.RegistrationRecord;
import com.orange.ussd.registration.data.model.RegistrationStatus;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class RegistrationDao_Impl implements RegistrationDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<RegistrationRecord> __insertionAdapterOfRegistrationRecord;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<RegistrationRecord> __deletionAdapterOfRegistrationRecord;

  private final EntityDeletionOrUpdateAdapter<RegistrationRecord> __updateAdapterOfRegistrationRecord;

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatus;

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatusWithError;

  private final SharedSQLiteStatement __preparedStmtOfUpdateUssdExecuted;

  private final SharedSQLiteStatement __preparedStmtOfUpdateNameFilled;

  private final SharedSQLiteStatement __preparedStmtOfUpdateCneFilled;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public RegistrationDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfRegistrationRecord = new EntityInsertionAdapter<RegistrationRecord>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `registration_records` (`id`,`phoneNumber`,`pukLastFour`,`fullName`,`cne`,`status`,`errorMessage`,`timestamp`,`ussdExecuted`,`nameFilled`,`cneFilled`,`completed`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RegistrationRecord entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getPhoneNumber());
        statement.bindString(3, entity.getPukLastFour());
        statement.bindString(4, entity.getFullName());
        statement.bindString(5, entity.getCne());
        final String _tmp = __converters.fromRegistrationStatus(entity.getStatus());
        statement.bindString(6, _tmp);
        if (entity.getErrorMessage() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getErrorMessage());
        }
        statement.bindLong(8, entity.getTimestamp());
        final int _tmp_1 = entity.getUssdExecuted() ? 1 : 0;
        statement.bindLong(9, _tmp_1);
        final int _tmp_2 = entity.getNameFilled() ? 1 : 0;
        statement.bindLong(10, _tmp_2);
        final int _tmp_3 = entity.getCneFilled() ? 1 : 0;
        statement.bindLong(11, _tmp_3);
        final int _tmp_4 = entity.getCompleted() ? 1 : 0;
        statement.bindLong(12, _tmp_4);
      }
    };
    this.__deletionAdapterOfRegistrationRecord = new EntityDeletionOrUpdateAdapter<RegistrationRecord>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `registration_records` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RegistrationRecord entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfRegistrationRecord = new EntityDeletionOrUpdateAdapter<RegistrationRecord>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `registration_records` SET `id` = ?,`phoneNumber` = ?,`pukLastFour` = ?,`fullName` = ?,`cne` = ?,`status` = ?,`errorMessage` = ?,`timestamp` = ?,`ussdExecuted` = ?,`nameFilled` = ?,`cneFilled` = ?,`completed` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RegistrationRecord entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getPhoneNumber());
        statement.bindString(3, entity.getPukLastFour());
        statement.bindString(4, entity.getFullName());
        statement.bindString(5, entity.getCne());
        final String _tmp = __converters.fromRegistrationStatus(entity.getStatus());
        statement.bindString(6, _tmp);
        if (entity.getErrorMessage() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getErrorMessage());
        }
        statement.bindLong(8, entity.getTimestamp());
        final int _tmp_1 = entity.getUssdExecuted() ? 1 : 0;
        statement.bindLong(9, _tmp_1);
        final int _tmp_2 = entity.getNameFilled() ? 1 : 0;
        statement.bindLong(10, _tmp_2);
        final int _tmp_3 = entity.getCneFilled() ? 1 : 0;
        statement.bindLong(11, _tmp_3);
        final int _tmp_4 = entity.getCompleted() ? 1 : 0;
        statement.bindLong(12, _tmp_4);
        statement.bindLong(13, entity.getId());
      }
    };
    this.__preparedStmtOfUpdateStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE registration_records SET status = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateStatusWithError = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE registration_records SET status = ?, errorMessage = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateUssdExecuted = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE registration_records SET ussdExecuted = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateNameFilled = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE registration_records SET nameFilled = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateCneFilled = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE registration_records SET cneFilled = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM registration_records";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final RegistrationRecord record,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfRegistrationRecord.insertAndReturnId(record);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<RegistrationRecord> records,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfRegistrationRecord.insert(records);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final RegistrationRecord record,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfRegistrationRecord.handle(record);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final RegistrationRecord record,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfRegistrationRecord.handle(record);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateStatus(final long id, final RegistrationStatus status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatus.acquire();
        int _argIndex = 1;
        final String _tmp = __converters.fromRegistrationStatus(status);
        _stmt.bindString(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateStatusWithError(final long id, final RegistrationStatus status,
      final String errorMessage, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatusWithError.acquire();
        int _argIndex = 1;
        final String _tmp = __converters.fromRegistrationStatus(status);
        _stmt.bindString(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindString(_argIndex, errorMessage);
        _argIndex = 3;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateStatusWithError.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateUssdExecuted(final long id, final boolean executed,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateUssdExecuted.acquire();
        int _argIndex = 1;
        final int _tmp = executed ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateUssdExecuted.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateNameFilled(final long id, final boolean filled,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateNameFilled.acquire();
        int _argIndex = 1;
        final int _tmp = filled ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateNameFilled.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateCneFilled(final long id, final boolean filled,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateCneFilled.acquire();
        int _argIndex = 1;
        final int _tmp = filled ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateCneFilled.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<RegistrationRecord>> getAllRecords() {
    final String _sql = "SELECT * FROM registration_records ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"registration_records"}, new Callable<List<RegistrationRecord>>() {
      @Override
      @NonNull
      public List<RegistrationRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfPukLastFour = CursorUtil.getColumnIndexOrThrow(_cursor, "pukLastFour");
          final int _cursorIndexOfFullName = CursorUtil.getColumnIndexOrThrow(_cursor, "fullName");
          final int _cursorIndexOfCne = CursorUtil.getColumnIndexOrThrow(_cursor, "cne");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfErrorMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "errorMessage");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfUssdExecuted = CursorUtil.getColumnIndexOrThrow(_cursor, "ussdExecuted");
          final int _cursorIndexOfNameFilled = CursorUtil.getColumnIndexOrThrow(_cursor, "nameFilled");
          final int _cursorIndexOfCneFilled = CursorUtil.getColumnIndexOrThrow(_cursor, "cneFilled");
          final int _cursorIndexOfCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "completed");
          final List<RegistrationRecord> _result = new ArrayList<RegistrationRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RegistrationRecord _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpPukLastFour;
            _tmpPukLastFour = _cursor.getString(_cursorIndexOfPukLastFour);
            final String _tmpFullName;
            _tmpFullName = _cursor.getString(_cursorIndexOfFullName);
            final String _tmpCne;
            _tmpCne = _cursor.getString(_cursorIndexOfCne);
            final RegistrationStatus _tmpStatus;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toRegistrationStatus(_tmp);
            final String _tmpErrorMessage;
            if (_cursor.isNull(_cursorIndexOfErrorMessage)) {
              _tmpErrorMessage = null;
            } else {
              _tmpErrorMessage = _cursor.getString(_cursorIndexOfErrorMessage);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpUssdExecuted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfUssdExecuted);
            _tmpUssdExecuted = _tmp_1 != 0;
            final boolean _tmpNameFilled;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfNameFilled);
            _tmpNameFilled = _tmp_2 != 0;
            final boolean _tmpCneFilled;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfCneFilled);
            _tmpCneFilled = _tmp_3 != 0;
            final boolean _tmpCompleted;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfCompleted);
            _tmpCompleted = _tmp_4 != 0;
            _item = new RegistrationRecord(_tmpId,_tmpPhoneNumber,_tmpPukLastFour,_tmpFullName,_tmpCne,_tmpStatus,_tmpErrorMessage,_tmpTimestamp,_tmpUssdExecuted,_tmpNameFilled,_tmpCneFilled,_tmpCompleted);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getRecordById(final long id,
      final Continuation<? super RegistrationRecord> $completion) {
    final String _sql = "SELECT * FROM registration_records WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<RegistrationRecord>() {
      @Override
      @Nullable
      public RegistrationRecord call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfPukLastFour = CursorUtil.getColumnIndexOrThrow(_cursor, "pukLastFour");
          final int _cursorIndexOfFullName = CursorUtil.getColumnIndexOrThrow(_cursor, "fullName");
          final int _cursorIndexOfCne = CursorUtil.getColumnIndexOrThrow(_cursor, "cne");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfErrorMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "errorMessage");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfUssdExecuted = CursorUtil.getColumnIndexOrThrow(_cursor, "ussdExecuted");
          final int _cursorIndexOfNameFilled = CursorUtil.getColumnIndexOrThrow(_cursor, "nameFilled");
          final int _cursorIndexOfCneFilled = CursorUtil.getColumnIndexOrThrow(_cursor, "cneFilled");
          final int _cursorIndexOfCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "completed");
          final RegistrationRecord _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpPukLastFour;
            _tmpPukLastFour = _cursor.getString(_cursorIndexOfPukLastFour);
            final String _tmpFullName;
            _tmpFullName = _cursor.getString(_cursorIndexOfFullName);
            final String _tmpCne;
            _tmpCne = _cursor.getString(_cursorIndexOfCne);
            final RegistrationStatus _tmpStatus;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toRegistrationStatus(_tmp);
            final String _tmpErrorMessage;
            if (_cursor.isNull(_cursorIndexOfErrorMessage)) {
              _tmpErrorMessage = null;
            } else {
              _tmpErrorMessage = _cursor.getString(_cursorIndexOfErrorMessage);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpUssdExecuted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfUssdExecuted);
            _tmpUssdExecuted = _tmp_1 != 0;
            final boolean _tmpNameFilled;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfNameFilled);
            _tmpNameFilled = _tmp_2 != 0;
            final boolean _tmpCneFilled;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfCneFilled);
            _tmpCneFilled = _tmp_3 != 0;
            final boolean _tmpCompleted;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfCompleted);
            _tmpCompleted = _tmp_4 != 0;
            _result = new RegistrationRecord(_tmpId,_tmpPhoneNumber,_tmpPukLastFour,_tmpFullName,_tmpCne,_tmpStatus,_tmpErrorMessage,_tmpTimestamp,_tmpUssdExecuted,_tmpNameFilled,_tmpCneFilled,_tmpCompleted);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getRecordByPhoneNumber(final String phoneNumber,
      final Continuation<? super RegistrationRecord> $completion) {
    final String _sql = "SELECT * FROM registration_records WHERE phoneNumber = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, phoneNumber);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<RegistrationRecord>() {
      @Override
      @Nullable
      public RegistrationRecord call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfPukLastFour = CursorUtil.getColumnIndexOrThrow(_cursor, "pukLastFour");
          final int _cursorIndexOfFullName = CursorUtil.getColumnIndexOrThrow(_cursor, "fullName");
          final int _cursorIndexOfCne = CursorUtil.getColumnIndexOrThrow(_cursor, "cne");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfErrorMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "errorMessage");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfUssdExecuted = CursorUtil.getColumnIndexOrThrow(_cursor, "ussdExecuted");
          final int _cursorIndexOfNameFilled = CursorUtil.getColumnIndexOrThrow(_cursor, "nameFilled");
          final int _cursorIndexOfCneFilled = CursorUtil.getColumnIndexOrThrow(_cursor, "cneFilled");
          final int _cursorIndexOfCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "completed");
          final RegistrationRecord _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpPukLastFour;
            _tmpPukLastFour = _cursor.getString(_cursorIndexOfPukLastFour);
            final String _tmpFullName;
            _tmpFullName = _cursor.getString(_cursorIndexOfFullName);
            final String _tmpCne;
            _tmpCne = _cursor.getString(_cursorIndexOfCne);
            final RegistrationStatus _tmpStatus;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toRegistrationStatus(_tmp);
            final String _tmpErrorMessage;
            if (_cursor.isNull(_cursorIndexOfErrorMessage)) {
              _tmpErrorMessage = null;
            } else {
              _tmpErrorMessage = _cursor.getString(_cursorIndexOfErrorMessage);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpUssdExecuted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfUssdExecuted);
            _tmpUssdExecuted = _tmp_1 != 0;
            final boolean _tmpNameFilled;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfNameFilled);
            _tmpNameFilled = _tmp_2 != 0;
            final boolean _tmpCneFilled;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfCneFilled);
            _tmpCneFilled = _tmp_3 != 0;
            final boolean _tmpCompleted;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfCompleted);
            _tmpCompleted = _tmp_4 != 0;
            _result = new RegistrationRecord(_tmpId,_tmpPhoneNumber,_tmpPukLastFour,_tmpFullName,_tmpCne,_tmpStatus,_tmpErrorMessage,_tmpTimestamp,_tmpUssdExecuted,_tmpNameFilled,_tmpCneFilled,_tmpCompleted);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<RegistrationRecord>> getRecordsByStatus(final RegistrationStatus status) {
    final String _sql = "SELECT * FROM registration_records WHERE status = ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final String _tmp = __converters.fromRegistrationStatus(status);
    _statement.bindString(_argIndex, _tmp);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"registration_records"}, new Callable<List<RegistrationRecord>>() {
      @Override
      @NonNull
      public List<RegistrationRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfPukLastFour = CursorUtil.getColumnIndexOrThrow(_cursor, "pukLastFour");
          final int _cursorIndexOfFullName = CursorUtil.getColumnIndexOrThrow(_cursor, "fullName");
          final int _cursorIndexOfCne = CursorUtil.getColumnIndexOrThrow(_cursor, "cne");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfErrorMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "errorMessage");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfUssdExecuted = CursorUtil.getColumnIndexOrThrow(_cursor, "ussdExecuted");
          final int _cursorIndexOfNameFilled = CursorUtil.getColumnIndexOrThrow(_cursor, "nameFilled");
          final int _cursorIndexOfCneFilled = CursorUtil.getColumnIndexOrThrow(_cursor, "cneFilled");
          final int _cursorIndexOfCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "completed");
          final List<RegistrationRecord> _result = new ArrayList<RegistrationRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RegistrationRecord _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpPukLastFour;
            _tmpPukLastFour = _cursor.getString(_cursorIndexOfPukLastFour);
            final String _tmpFullName;
            _tmpFullName = _cursor.getString(_cursorIndexOfFullName);
            final String _tmpCne;
            _tmpCne = _cursor.getString(_cursorIndexOfCne);
            final RegistrationStatus _tmpStatus;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toRegistrationStatus(_tmp_1);
            final String _tmpErrorMessage;
            if (_cursor.isNull(_cursorIndexOfErrorMessage)) {
              _tmpErrorMessage = null;
            } else {
              _tmpErrorMessage = _cursor.getString(_cursorIndexOfErrorMessage);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpUssdExecuted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfUssdExecuted);
            _tmpUssdExecuted = _tmp_2 != 0;
            final boolean _tmpNameFilled;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfNameFilled);
            _tmpNameFilled = _tmp_3 != 0;
            final boolean _tmpCneFilled;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfCneFilled);
            _tmpCneFilled = _tmp_4 != 0;
            final boolean _tmpCompleted;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfCompleted);
            _tmpCompleted = _tmp_5 != 0;
            _item = new RegistrationRecord(_tmpId,_tmpPhoneNumber,_tmpPukLastFour,_tmpFullName,_tmpCne,_tmpStatus,_tmpErrorMessage,_tmpTimestamp,_tmpUssdExecuted,_tmpNameFilled,_tmpCneFilled,_tmpCompleted);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getNextPendingRecord(final RegistrationStatus status,
      final Continuation<? super RegistrationRecord> $completion) {
    final String _sql = "SELECT * FROM registration_records WHERE status = ? ORDER BY id ASC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final String _tmp = __converters.fromRegistrationStatus(status);
    _statement.bindString(_argIndex, _tmp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<RegistrationRecord>() {
      @Override
      @Nullable
      public RegistrationRecord call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfPukLastFour = CursorUtil.getColumnIndexOrThrow(_cursor, "pukLastFour");
          final int _cursorIndexOfFullName = CursorUtil.getColumnIndexOrThrow(_cursor, "fullName");
          final int _cursorIndexOfCne = CursorUtil.getColumnIndexOrThrow(_cursor, "cne");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfErrorMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "errorMessage");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfUssdExecuted = CursorUtil.getColumnIndexOrThrow(_cursor, "ussdExecuted");
          final int _cursorIndexOfNameFilled = CursorUtil.getColumnIndexOrThrow(_cursor, "nameFilled");
          final int _cursorIndexOfCneFilled = CursorUtil.getColumnIndexOrThrow(_cursor, "cneFilled");
          final int _cursorIndexOfCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "completed");
          final RegistrationRecord _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final String _tmpPukLastFour;
            _tmpPukLastFour = _cursor.getString(_cursorIndexOfPukLastFour);
            final String _tmpFullName;
            _tmpFullName = _cursor.getString(_cursorIndexOfFullName);
            final String _tmpCne;
            _tmpCne = _cursor.getString(_cursorIndexOfCne);
            final RegistrationStatus _tmpStatus;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toRegistrationStatus(_tmp_1);
            final String _tmpErrorMessage;
            if (_cursor.isNull(_cursorIndexOfErrorMessage)) {
              _tmpErrorMessage = null;
            } else {
              _tmpErrorMessage = _cursor.getString(_cursorIndexOfErrorMessage);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpUssdExecuted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfUssdExecuted);
            _tmpUssdExecuted = _tmp_2 != 0;
            final boolean _tmpNameFilled;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfNameFilled);
            _tmpNameFilled = _tmp_3 != 0;
            final boolean _tmpCneFilled;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfCneFilled);
            _tmpCneFilled = _tmp_4 != 0;
            final boolean _tmpCompleted;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfCompleted);
            _tmpCompleted = _tmp_5 != 0;
            _result = new RegistrationRecord(_tmpId,_tmpPhoneNumber,_tmpPukLastFour,_tmpFullName,_tmpCne,_tmpStatus,_tmpErrorMessage,_tmpTimestamp,_tmpUssdExecuted,_tmpNameFilled,_tmpCneFilled,_tmpCompleted);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCountByStatus(final RegistrationStatus status,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM registration_records WHERE status = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final String _tmp = __converters.fromRegistrationStatus(status);
    _statement.bindString(_argIndex, _tmp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(0);
            _result = _tmp_1;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getTotalCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM registration_records";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
