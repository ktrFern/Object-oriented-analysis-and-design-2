using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;

namespace BankTerminal
{
    class BankAccount
    {
        public string Owner { get; }
        public string Number { get; }

        private decimal _balance;
        public decimal Balance { get { return _balance; } }

        public BankAccount(string owner, string number, decimal initialBalance)
        {
            Owner = owner;
            Number = number;
            _balance = initialBalance;
        }

        public void Credit(decimal amount)
        {
            _balance += amount;
        }

        public void Debit(decimal amount)
        {
            if (_balance < amount)
                throw new InvalidOperationException(
                    string.Format(
                        "Недостаточно средств на счёте {0}.\nБаланс: {1:N2} руб., требуется: {2:N2} руб.",
                        Number, _balance, amount));
            _balance -= amount;
        }

        public override string ToString()
        {
            return string.Format("{0} — {1}", Number, Owner);
        }
    }

    enum OperationType
    {
        Deposit,
        Withdraw,
        Transfer,
        Macro
    }

    class HistoryEntry
    {
        public OperationType Type { get; }
        public BankAccount AccountFrom { get; }
        public BankAccount AccountTo { get; }
        public decimal Amount { get; }
        public string Description { get; }
        public List<HistoryEntry> SubEntries { get; }

        public HistoryEntry(OperationType type, BankAccount from, BankAccount to, decimal amount, string description, List<HistoryEntry> subEntries = null)
        {
            Type = type;
            AccountFrom = from;
            AccountTo = to;
            Amount = amount;
            Description = description;
            SubEntries = subEntries;
        }
    }

    class QueueEntry
    {
        public OperationType Type { get; }
        public BankAccount AccountFrom { get; }
        public BankAccount AccountTo { get; }
        public decimal Amount { get; }
        public string MacroName { get; }
        public List<QueueEntry> SubEntries { get; }

        public QueueEntry(OperationType type, BankAccount from, BankAccount to, decimal amount, string macroName = null, List<QueueEntry> subEntries = null)
        {
            Type = type;
            AccountFrom = from;
            AccountTo = to;
            Amount = amount;
            MacroName = macroName;
            SubEntries = subEntries;
        }

        public string Description
        {
            get
            {
                switch (Type)
                {
                    case OperationType.Deposit:
                        return string.Format("Пополнение счёта {0} ({1}) на сумму {2:N2} руб.",
                            AccountFrom.Number, AccountFrom.Owner, Amount);
                    case OperationType.Withdraw:
                        return string.Format("Снятие {0:N2} руб. со счёта {1} ({2})",
                            Amount, AccountFrom.Number, AccountFrom.Owner);
                    case OperationType.Transfer:
                        return string.Format("Перевод {0:N2} руб. со счёта {1} ({2}) на счёт {3} ({4})",
                            Amount, AccountFrom.Number, AccountFrom.Owner, AccountTo.Number, AccountTo.Owner);
                    case OperationType.Macro:
                        return string.Format("Пакет \"{0}\" ({1} операций)", MacroName, SubEntries != null ? SubEntries.Count : 0);
                    default:
                        return "";
                }
            }
        }

        public string TypeLabel
        {
            get
            {
                switch (Type)
                {
                    case OperationType.Deposit: return "Депозит";
                    case OperationType.Withdraw: return "Снятие";
                    case OperationType.Transfer: return "Перевод";
                    case OperationType.Macro: return "Макро";
                    default: return "";
                }
            }
        }
    }

    class BankTerminal
    {
        private readonly Stack<HistoryEntry> _history = new Stack<HistoryEntry>();
        private readonly Stack<HistoryEntry> _redoStack = new Stack<HistoryEntry>();
        private readonly Queue<QueueEntry> _queue = new Queue<QueueEntry>();
        private readonly List<LogEntry> _log = new List<LogEntry>();

        public IReadOnlyList<LogEntry> Log { get { return _log; } }
        public int QueueCount { get { return _queue.Count; } }
        public bool CanUndo { get { return _history.Count > 0; } }
        public bool CanRedo { get { return _redoStack.Count > 0; } }

        public OperationResult Deposit(BankAccount account, decimal amount)
        {
            try
            {
                account.Credit(amount);
                var desc = string.Format("Пополнение счёта {0} ({1}) на сумму {2:N2} руб.",
                    account.Number, account.Owner, amount);
                var entry = new HistoryEntry(OperationType.Deposit, account, null, amount, desc);
                _history.Push(entry);
                _redoStack.Clear();
                AddLog("OK", desc);
                return OperationResult.Success(desc);
            }
            catch (InvalidOperationException ex)
            {
                AddLog("ОШИБКА", ex.Message);
                return OperationResult.Fail(ex.Message);
            }
        }

        public OperationResult Withdraw(BankAccount account, decimal amount)
        {
            try
            {
                account.Debit(amount);
                var desc = string.Format("Снятие {0:N2} руб. со счёта {1} ({2})",
                    amount, account.Number, account.Owner);
                var entry = new HistoryEntry(OperationType.Withdraw, account, null, amount, desc);
                _history.Push(entry);
                _redoStack.Clear();
                AddLog("OK", desc);
                return OperationResult.Success(desc);
            }
            catch (InvalidOperationException ex)
            {
                AddLog("ОШИБКА", ex.Message);
                return OperationResult.Fail(ex.Message);
            }
        }

        public OperationResult Transfer(BankAccount from, BankAccount to, decimal amount)
        {
            try
            {
                from.Debit(amount);
                to.Credit(amount);
                var desc = string.Format("Перевод {0:N2} руб. со счёта {1} ({2}) на счёт {3} ({4})",
                    amount, from.Number, from.Owner, to.Number, to.Owner);
                var entry = new HistoryEntry(OperationType.Transfer, from, to, amount, desc);
                _history.Push(entry);
                _redoStack.Clear();
                AddLog("OK", desc);
                return OperationResult.Success(desc);
            }
            catch (InvalidOperationException ex)
            {
                AddLog("ОШИБКА", ex.Message);
                return OperationResult.Fail(ex.Message);
            }
        }

        public OperationResult ExecuteMacro(string name, List<QueueEntry> operations)
        {
            var executed = new List<HistoryEntry>();
            try
            {
                foreach (var op in operations)
                {
                    switch (op.Type)
                    {
                        case OperationType.Deposit:
                            op.AccountFrom.Credit(op.Amount);
                            break;
                        case OperationType.Withdraw:
                            op.AccountFrom.Debit(op.Amount);
                            break;
                        case OperationType.Transfer:
                            op.AccountFrom.Debit(op.Amount);
                            op.AccountTo.Credit(op.Amount);
                            break;
                    }
                    executed.Add(new HistoryEntry(op.Type, op.AccountFrom, op.AccountTo, op.Amount, op.Description));
                }
                var desc = string.Format("Пакет \"{0}\" ({1} операций)", name, operations.Count);
                var macroEntry = new HistoryEntry(OperationType.Macro, null, null, 0, desc, executed);
                _history.Push(macroEntry);
                _redoStack.Clear();
                AddLog("OK", desc);
                return OperationResult.Success(desc);
            }
            catch (InvalidOperationException ex)
            {
                for (int i = executed.Count - 1; i >= 0; i--)
                {
                    var e = executed[i];
                    if (e.Type == OperationType.Deposit)
                        e.AccountFrom.Debit(e.Amount);
                    else if (e.Type == OperationType.Withdraw)
                        e.AccountFrom.Credit(e.Amount);
                    else if (e.Type == OperationType.Transfer)
                    {
                        e.AccountTo.Debit(e.Amount);
                        e.AccountFrom.Credit(e.Amount);
                    }
                }
                AddLog("ОШИБКА", ex.Message);
                return OperationResult.Fail(ex.Message);
            }
        }

        public void EnqueueDeposit(BankAccount account, decimal amount)
        {
            var entry = new QueueEntry(OperationType.Deposit, account, null, amount);
            _queue.Enqueue(entry);
            AddLog("ОЧЕРЕДЬ", entry.Description);
        }

        public void EnqueueWithdraw(BankAccount account, decimal amount)
        {
            var entry = new QueueEntry(OperationType.Withdraw, account, null, amount);
            _queue.Enqueue(entry);
            AddLog("ОЧЕРЕДЬ", entry.Description);
        }

        public void EnqueueTransfer(BankAccount from, BankAccount to, decimal amount)
        {
            var entry = new QueueEntry(OperationType.Transfer, from, to, amount);
            _queue.Enqueue(entry);
            AddLog("ОЧЕРЕДЬ", entry.Description);
        }

        public void EnqueueMacro(string name, List<QueueEntry> operations)
        {
            var entry = new QueueEntry(OperationType.Macro, null, null, 0, name, new List<QueueEntry>(operations));
            _queue.Enqueue(entry);
            AddLog("ОЧЕРЕДЬ", entry.Description);
        }

        public List<OperationResult> FlushQueue()
        {
            var results = new List<OperationResult>();
            while (_queue.Count > 0)
            {
                var entry = _queue.Dequeue();
                OperationResult result;
                switch (entry.Type)
                {
                    case OperationType.Deposit:
                        result = Deposit(entry.AccountFrom, entry.Amount);
                        break;
                    case OperationType.Withdraw:
                        result = Withdraw(entry.AccountFrom, entry.Amount);
                        break;
                    case OperationType.Transfer:
                        result = Transfer(entry.AccountFrom, entry.AccountTo, entry.Amount);
                        break;
                    case OperationType.Macro:
                        result = ExecuteMacro(entry.MacroName, entry.SubEntries);
                        break;
                    default:
                        result = OperationResult.Fail("Неизвестный тип операции");
                        break;
                }
                results.Add(result);
            }
            return results;
        }

        public IEnumerable<QueueEntry> PeekQueue()
        {
            return _queue;
        }

        public OperationResult Undo()
        {
            if (!CanUndo) return OperationResult.Fail("Нечего отменять.");
            var entry = _history.Pop();
            try
            {
                RollbackEntry(entry);
                _redoStack.Push(entry);
                AddLog("ОТМЕНА", "Операция отменена: " + entry.Description);
                return OperationResult.Success("Операция отменена: " + entry.Description);
            }
            catch (InvalidOperationException ex)
            {
                _history.Push(entry);
                AddLog("ОШИБКА ОТМЕНЫ", ex.Message);
                return OperationResult.Fail(ex.Message);
            }
        }

        public OperationResult Redo()
        {
            if (!CanRedo) return OperationResult.Fail("Нечего повторять.");
            var entry = _redoStack.Pop();
            try
            {
                ReapplyEntry(entry);
                _history.Push(entry);
                AddLog("OK", entry.Description);
                return OperationResult.Success(entry.Description);
            }
            catch (InvalidOperationException ex)
            {
                _redoStack.Push(entry);
                AddLog("ОШИБКА", ex.Message);
                return OperationResult.Fail(ex.Message);
            }
        }

        private void RollbackEntry(HistoryEntry entry)
        {
            switch (entry.Type)
            {
                case OperationType.Deposit:
                    entry.AccountFrom.Debit(entry.Amount);
                    break;
                case OperationType.Withdraw:
                    entry.AccountFrom.Credit(entry.Amount);
                    break;
                case OperationType.Transfer:
                    entry.AccountTo.Debit(entry.Amount);
                    entry.AccountFrom.Credit(entry.Amount);
                    break;
                case OperationType.Macro:
                    for (int i = entry.SubEntries.Count - 1; i >= 0; i--)
                        RollbackEntry(entry.SubEntries[i]);
                    break;
            }
        }

        private void ReapplyEntry(HistoryEntry entry)
        {
            switch (entry.Type)
            {
                case OperationType.Deposit:
                    entry.AccountFrom.Credit(entry.Amount);
                    break;
                case OperationType.Withdraw:
                    entry.AccountFrom.Debit(entry.Amount);
                    break;
                case OperationType.Transfer:
                    entry.AccountFrom.Debit(entry.Amount);
                    entry.AccountTo.Credit(entry.Amount);
                    break;
                case OperationType.Macro:
                    foreach (var sub in entry.SubEntries)
                        ReapplyEntry(sub);
                    break;
            }
        }

        public void SaveLog(string path)
        {
            File.WriteAllLines(path,
                _log.Select(e =>
                    string.Format("{0:yyyy-MM-dd HH:mm:ss}  [{1}]  {2}",
                        e.Time, e.Status, e.Description))
                    .ToArray());
        }

        private void AddLog(string status, string description)
        {
            _log.Add(new LogEntry(DateTime.Now, status, description));
        }
    }

    class LogEntry
    {
        public DateTime Time { get; }
        public string Status { get; }
        public string Description { get; }

        public LogEntry(DateTime time, string status, string description)
        {
            Time = time;
            Status = status;
            Description = description;
        }
    }

    class OperationResult
    {
        public bool IsSuccess { get; private set; }
        public string Message { get; private set; }

        private OperationResult() { }

        public static OperationResult Success(string msg)
        {
            return new OperationResult { IsSuccess = true, Message = msg };
        }

        public static OperationResult Fail(string msg)
        {
            return new OperationResult { IsSuccess = false, Message = msg };
        }
    }
}
