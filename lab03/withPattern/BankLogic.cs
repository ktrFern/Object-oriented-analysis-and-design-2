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

    abstract class BankCommand
    {
        public DateTime CreatedAt { get; } = DateTime.Now;

        public abstract string Description { get; }
        public abstract string Type { get; }

        public abstract void Execute();
        public abstract void Unexecute();
    }

    class DepositCommand : BankCommand
    {
        private readonly BankAccount _account;
        private readonly decimal _amount;

        public DepositCommand(BankAccount account, decimal amount)
        {
            _account = account;
            _amount = amount;
        }

        public override string Type { get { return "Депозит"; } }
        public override string Description
        {
            get
            {
                return string.Format("Пополнение счёта {0} ({1}) на сумму {2:N2} руб.",
                    _account.Number, _account.Owner, _amount);
            }
        }

        public override void Execute() { _account.Credit(_amount); }
        public override void Unexecute() { _account.Debit(_amount); }
    }

    class WithdrawCommand : BankCommand
    {
        private readonly BankAccount _account;
        private readonly decimal _amount;

        public WithdrawCommand(BankAccount account, decimal amount)
        {
            _account = account;
            _amount = amount;
        }

        public override string Type { get { return "Снятие"; } }
        public override string Description
        {
            get
            {
                return string.Format("Снятие {0:N2} руб. со счёта {1} ({2})",
                    _amount, _account.Number, _account.Owner);
            }
        }

        public override void Execute() { _account.Debit(_amount); }
        public override void Unexecute() { _account.Credit(_amount); }
    }

    class TransferCommand : BankCommand
    {
        private readonly BankAccount _from;
        private readonly BankAccount _to;
        private readonly decimal _amount;

        public TransferCommand(BankAccount from, BankAccount to, decimal amount)
        {
            _from = from;
            _to = to;
            _amount = amount;
        }

        public override string Type { get { return "Перевод"; } }
        public override string Description
        {
            get
            {
                return string.Format("Перевод {0:N2} руб. со счёта {1} ({2}) на счёт {3} ({4})",
                    _amount, _from.Number, _from.Owner, _to.Number, _to.Owner);
            }
        }

        public override void Execute()
        {
            _from.Debit(_amount);
            _to.Credit(_amount);
        }

        public override void Unexecute()
        {
            _to.Debit(_amount);
            _from.Credit(_amount);
        }
    }

    class MacroCommand : BankCommand
    {
        private readonly List<BankCommand> _commands;
        private readonly string _name;
        private readonly List<BankCommand> _executed = new List<BankCommand>();

        public MacroCommand(string name, IEnumerable<BankCommand> commands)
        {
            _name = name;
            _commands = new List<BankCommand>(commands);
        }

        public override string Type { get { return "Макро"; } }
        public override string Description
        {
            get { return string.Format("Пакет \"{0}\" ({1} операций)", _name, _commands.Count); }
        }

        public IReadOnlyList<BankCommand> SubCommands { get { return _commands; } }

        public override void Execute()
        {
            _executed.Clear();
            foreach (var cmd in _commands)
            {
                cmd.Execute();
                _executed.Add(cmd);
            }
        }

        public override void Unexecute()
        {
            for (int i = _executed.Count - 1; i >= 0; i--)
                _executed[i].Unexecute();
            _executed.Clear();
        }
    }

    class BankTerminal
    {
        private readonly Stack<BankCommand> _history = new Stack<BankCommand>();
        private readonly Stack<BankCommand> _redoStack = new Stack<BankCommand>();
        private readonly Queue<BankCommand> _queue = new Queue<BankCommand>();
        private readonly List<LogEntry> _log = new List<LogEntry>();

        public IReadOnlyList<LogEntry> Log { get { return _log; } }
        public int QueueCount { get { return _queue.Count; } }
        public bool CanUndo { get { return _history.Count > 0; } }
        public bool CanRedo { get { return _redoStack.Count > 0; } }

        private OperationResult ExecuteInternal(BankCommand command)
        {
            try
            {
                command.Execute();
                _history.Push(command);
                AddLog("OK", command);
                return OperationResult.Success(command.Description);
            }
            catch (InvalidOperationException ex)
            {
                AddLog("ОШИБКА", command, ex.Message);
                return OperationResult.Fail(ex.Message);
            }
        }

        public OperationResult ExecuteNow(BankCommand command)
        {
            _redoStack.Clear();
            return ExecuteInternal(command);
        }

        public void Enqueue(BankCommand command)
        {
            _queue.Enqueue(command);
            AddLog("ОЧЕРЕДЬ", command);
        }

        public List<OperationResult> FlushQueue()
        {
            var results = new List<OperationResult>();
            while (_queue.Count > 0)
                results.Add(ExecuteNow(_queue.Dequeue()));
            return results;
        }

        public IEnumerable<BankCommand> PeekQueue()
        {
            return _queue;
        }

        public OperationResult Undo()
        {
            if (!CanUndo) return OperationResult.Fail("Нечего отменять.");
            var cmd = _history.Pop();
            try
            {
                cmd.Unexecute();
                _redoStack.Push(cmd);
                AddLog("ОТМЕНА", cmd);
                return OperationResult.Success("Операция отменена: " + cmd.Description);
            }
            catch (InvalidOperationException ex)
            {
                _history.Push(cmd);
                AddLog("ОШИБКА ОТМЕНЫ", cmd, ex.Message);
                return OperationResult.Fail(ex.Message);
            }
        }

        public OperationResult Redo()
        {
            if (!CanRedo) return OperationResult.Fail("Нечего повторять.");
            var cmd = _redoStack.Pop();
            return ExecuteInternal(cmd);
        }

        public void SaveLog(string path)
        {
            File.WriteAllLines(path,
                _log.Select(e =>
                    string.Format("{0:yyyy-MM-dd HH:mm:ss}  [{1}]  {2}",
                        e.Time, e.Status, e.Description))
                    .ToArray());
        }

        private void AddLog(string status, BankCommand cmd, string note = null)
        {
            var desc = note == null
                ? cmd.Description
                : cmd.Description + " — " + note;
            _log.Add(new LogEntry(DateTime.Now, status, desc));
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
