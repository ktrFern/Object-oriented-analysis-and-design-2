using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace BankTerminal
{
    public class MainForm : Form
    {
        private readonly List<BankAccount> _accounts;
        private readonly BankTerminal _terminal = new BankTerminal();
        private readonly List<BankCommand> _macroBuffer = new List<BankCommand>();

        private DataGridView dgvAccounts;
        private GroupBox grpOperation;
        private ComboBox cmbType;
        private ComboBox cmbFrom;
        private ComboBox cmbTo;
        private Label lblTo;
        private NumericUpDown nudAmount;
        private Button btnExecute;
        private Button btnEnqueue;
        private GroupBox grpMacro;
        private TextBox txtMacroName;
        private ListBox lstMacroOps;
        private Button btnAddToMacro;
        private Button btnClearMacro;
        private Button btnRunMacro;
        private Button btnQueueMacro;
        private GroupBox grpQueue;
        private ListBox lstQueue;
        private Button btnFlush;
        private TabControl tabs;
        private ListView lvLog;
        private Button btnUndo;
        private Button btnRedo;
        private Button btnSaveLog;
        private Label lblStatus;

        public MainForm()
        {
            _accounts = new List<BankAccount>
            {
                new BankAccount("Екатерина Волкова", "RU001", 50000m),
                new BankAccount("Михаил Соколов",   "RU002", 20000m),
                new BankAccount("Анна Морозова",    "RU003",  5000m),
            };

            InitUI();
            RefreshAll();
        }

        private void InitUI()
        {
            Text = "Банковский терминал — паттерн Команда";
            Size = new Size(1100, 720);
            MinimumSize = new Size(900, 600);
            StartPosition = FormStartPosition.CenterScreen;
            Font = new Font("Segoe UI", 9f);
            BackColor = Color.FromArgb(245, 247, 250);

            var bottom = new Panel
            {
                Dock = DockStyle.Bottom,
                Height = 44,
                BackColor = Color.White,
                Padding = new Padding(8, 6, 8, 6)
            };
            bottom.Paint += (s, e) =>
                e.Graphics.DrawLine(new Pen(Color.FromArgb(210, 215, 225)), 0, 0, bottom.Width, 0);

            btnUndo = MakeButton("↩ Отменить (Undo)", 0, 0, 160, Color.FromArgb(180, 120, 30));
            btnRedo = MakeButton("↪ Повторить (Redo)", 168, 0, 160, Color.FromArgb(80, 130, 180));
            btnSaveLog = MakeButton("Сохранить журнал", 336, 0, 160, Color.FromArgb(100, 100, 110));
            btnUndo.Height = btnRedo.Height = btnSaveLog.Height = 30;

            btnUndo.Click += (s, e) =>
            {
                var r = _terminal.Undo();
                RefreshAll();
                SetStatus(r.Message, r.IsSuccess);
            };
            btnRedo.Click += (s, e) =>
            {
                var r = _terminal.Redo();
                RefreshAll();
                SetStatus(r.Message, r.IsSuccess);
            };
            btnSaveLog.Click += (s, e) =>
            {
                using (var dlg = new SaveFileDialog { Filter = "Text files|*.txt", FileName = "audit_log.txt" })
                {
                    if (dlg.ShowDialog() == DialogResult.OK)
                    {
                        _terminal.SaveLog(dlg.FileName);
                        SetStatus("Журнал сохранён: " + dlg.FileName, true);
                    }
                }
            };

            lblStatus = new Label
            {
                Left = 510,
                Top = 10,
                AutoSize = true,
                Font = new Font("Segoe UI", 9f),
                ForeColor = Color.FromArgb(60, 60, 60)
            };

            bottom.Controls.AddRange(new Control[] { btnUndo, btnRedo, btnSaveLog, lblStatus });
            Controls.Add(bottom);

            var split = new SplitContainer
            {
                Dock = DockStyle.Fill,
                SplitterWidth = 5,
                FixedPanel = FixedPanel.Panel1,
                BackColor = Color.FromArgb(210, 215, 225)
            };
            Load += (s, e) =>
            {
                split.Panel1MinSize = 340;
                split.Panel2MinSize = 400;
                split.SplitterDistance = 370;
            };
            Controls.Add(split);

            var left = split.Panel1;
            left.Padding = new Padding(8);
            left.BackColor = Color.FromArgb(245, 247, 250);

            var right = split.Panel2;
            right.BackColor = Color.FromArgb(245, 247, 250);

            var grpAcc = MakeGroupBox("Счета клиентов", 0, 0, 344, 160);
            dgvAccounts = new DataGridView
            {
                Dock = DockStyle.Fill,
                ReadOnly = true,
                AllowUserToAddRows = false,
                AllowUserToDeleteRows = false,
                RowHeadersVisible = false,
                SelectionMode = DataGridViewSelectionMode.FullRowSelect,
                AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.Fill,
                BackgroundColor = Color.White,
                BorderStyle = BorderStyle.None,
                GridColor = Color.FromArgb(220, 225, 235)
            };
            dgvAccounts.Columns.Add(new DataGridViewTextBoxColumn { Name = "Number", HeaderText = "Счёт", FillWeight = 25 });
            dgvAccounts.Columns.Add(new DataGridViewTextBoxColumn { Name = "Owner", HeaderText = "Владелец", FillWeight = 45 });
            dgvAccounts.Columns.Add(new DataGridViewTextBoxColumn
            {
                Name = "Balance",
                HeaderText = "Баланс",
                FillWeight = 30,
                DefaultCellStyle = new DataGridViewCellStyle { Alignment = DataGridViewContentAlignment.MiddleRight }
            });
            grpAcc.Controls.Add(dgvAccounts);
            left.Controls.Add(grpAcc);

            grpOperation = MakeGroupBox("Новая операция", 0, 165, 344, 195);

            var lblType = new Label { Text = "Тип:", Left = 8, Top = 24, AutoSize = true };
            cmbType = new ComboBox { Left = 70, Top = 20, Width = 260, DropDownStyle = ComboBoxStyle.DropDownList };
            cmbType.Items.AddRange(new object[] { "Депозит", "Снятие", "Перевод" });
            cmbType.SelectedIndex = 0;
            cmbType.SelectedIndexChanged += (s, e) => UpdateOperationUI();

            var lblFrom = new Label { Text = "Счёт:", Left = 8, Top = 56, AutoSize = true };
            cmbFrom = new ComboBox { Left = 70, Top = 52, Width = 260, DropDownStyle = ComboBoxStyle.DropDownList };

            lblTo = new Label { Text = "Куда:", Left = 8, Top = 88, AutoSize = true, Visible = false };
            cmbTo = new ComboBox { Left = 70, Top = 84, Width = 260, DropDownStyle = ComboBoxStyle.DropDownList, Visible = false };

            var lblAmt = new Label { Text = "Сумма:", Left = 8, Top = 120, AutoSize = true };
            nudAmount = new NumericUpDown
            {
                Left = 70,
                Top = 116,
                Width = 160,
                Minimum = 1,
                Maximum = 10000000,
                Value = 1000,
                DecimalPlaces = 2,
                ThousandsSeparator = true,
                Increment = 500
            };

            btnExecute = MakeButton("▶ Выполнить", 70, 152, 120, Color.FromArgb(37, 122, 253));
            btnEnqueue = MakeButton("+ В очередь", 200, 152, 130, Color.FromArgb(90, 160, 90));
            btnExecute.Click += (s, e) => OnExecute(true);
            btnEnqueue.Click += (s, e) => OnExecute(false);

            grpOperation.Controls.AddRange(new Control[]
                { lblType, cmbType, lblFrom, cmbFrom, lblTo, cmbTo, lblAmt, nudAmount, btnExecute, btnEnqueue });
            left.Controls.Add(grpOperation);

            grpMacro = MakeGroupBox("Макрокоманда", 0, 365, 344, 215);

            var lblMN = new Label { Text = "Название:", Left = 8, Top = 24, AutoSize = true };
            txtMacroName = new TextBox { Left = 80, Top = 20, Width = 250, Text = "Ежемесячные платежи" };
            lstMacroOps = new ListBox { Left = 8, Top = 50, Width = 322, Height = 80, Font = new Font("Consolas", 8f) };

            btnAddToMacro = MakeButton("+ Добавить", 8, 136, 100, Color.FromArgb(90, 160, 90));
            btnClearMacro = MakeButton("Очистить", 114, 136, 90, Color.FromArgb(180, 60, 60));
            btnRunMacro = MakeButton("▶ Выполнить", 8, 168, 120, Color.FromArgb(37, 122, 253));
            btnQueueMacro = MakeButton("+ В очередь", 135, 168, 120, Color.FromArgb(90, 160, 90));

            btnAddToMacro.Click += OnAddToMacro;
            btnClearMacro.Click += (s, e) => { _macroBuffer.Clear(); RefreshMacroList(); };
            btnRunMacro.Click += (s, e) => OnRunMacro(true);
            btnQueueMacro.Click += (s, e) => OnRunMacro(false);

            grpMacro.Controls.AddRange(new Control[]
                { lblMN, txtMacroName, lstMacroOps, btnAddToMacro, btnClearMacro, btnRunMacro, btnQueueMacro });
            left.Controls.Add(grpMacro);

            var rightSplit = new SplitContainer
            {
                Dock = DockStyle.Fill,
                Orientation = Orientation.Horizontal,
                SplitterWidth = 5,
                BackColor = Color.FromArgb(210, 215, 225)
            };
            Load += (s, e) =>
            {
                rightSplit.Panel1MinSize = 100;
                rightSplit.Panel2MinSize = 150;
                rightSplit.SplitterDistance = 180;
            };
            right.Controls.Add(rightSplit);

            var topPanel = rightSplit.Panel1;
            topPanel.BackColor = Color.FromArgb(245, 247, 250);

            var botPanel = rightSplit.Panel2;
            botPanel.BackColor = Color.FromArgb(245, 247, 250);

            grpQueue = MakeGroupBox("Очередь отложенных операций", 0, 0, 0, 0);
            grpQueue.Dock = DockStyle.Fill;

            lstQueue = new ListBox { Dock = DockStyle.Fill, Font = new Font("Consolas", 8.5f) };
            btnFlush = MakeButton("▶▶ Выполнить всю очередь", 0, 0, 0, Color.FromArgb(37, 122, 253));
            btnFlush.Dock = DockStyle.Bottom;
            btnFlush.Height = 30;
            btnFlush.Click += (s, e) =>
            {
                var results = _terminal.FlushQueue();
                RefreshAll();
                var lines = results.Select(r => (r.IsSuccess ? "OK: " : "Ошибка: ") + r.Message).ToArray();
                SetStatus(lines.Length > 0 ? string.Join("  ", lines) : "Очередь была пуста.", results.All(r => r.IsSuccess));
            };
            grpQueue.Controls.Add(lstQueue);
            grpQueue.Controls.Add(btnFlush);
            topPanel.Controls.Add(grpQueue);

            tabs = new TabControl { Dock = DockStyle.Fill };
            var tabLog = new TabPage("Журнал операций");

            lvLog = MakeListView();
            lvLog.Columns.Add("Время", 130);
            lvLog.Columns.Add("Статус", 90);
            lvLog.Columns.Add("Описание", -2);
            lvLog.Scrollable = true;
            tabLog.Controls.Add(lvLog);

            tabs.TabPages.Add(tabLog);
            botPanel.Controls.Add(tabs);

            KeyPreview = true;
            KeyDown += (s, e) =>
            {
                if (e.Control && e.KeyCode == Keys.Z) btnUndo.PerformClick();
                if (e.Control && e.KeyCode == Keys.Y) btnRedo.PerformClick();
            };

            RefreshAccountCombos();
            UpdateOperationUI();
        }

        private void OnExecute(bool immediate)
        {
            var from = cmbFrom.SelectedItem as BankAccount;
            if (from == null) return;

            decimal amount = nudAmount.Value;
            BankCommand cmd = null;

            switch (cmbType.SelectedIndex)
            {
                case 0:
                    cmd = new DepositCommand(from, amount);
                    break;
                case 1:
                    cmd = new WithdrawCommand(from, amount);
                    break;
                case 2:
                    var to = cmbTo.SelectedItem as BankAccount;
                    if (to == null || to == from)
                    { SetStatus("Выберите другой счёт получателя.", false); return; }
                    cmd = new TransferCommand(from, to, amount);
                    break;
            }

            if (cmd == null) return;

            if (immediate)
            {
                var r = _terminal.ExecuteNow(cmd);
                RefreshAll();
                SetStatus(r.Message, r.IsSuccess);
            }
            else
            {
                _terminal.Enqueue(cmd);
                RefreshAll();
                SetStatus("Добавлено в очередь: " + cmd.Description, true);
            }
        }

        private void OnAddToMacro(object sender, EventArgs e)
        {
            var from = cmbFrom.SelectedItem as BankAccount;
            if (from == null) return;

            decimal amount = nudAmount.Value;
            BankCommand cmd = null;

            switch (cmbType.SelectedIndex)
            {
                case 0:
                    cmd = new DepositCommand(from, amount);
                    break;
                case 1:
                    cmd = new WithdrawCommand(from, amount);
                    break;
                case 2:
                    var to = cmbTo.SelectedItem as BankAccount;
                    if (to == null || to == from)
                    { SetStatus("Выберите другой счёт получателя.", false); return; }
                    cmd = new TransferCommand(from, to, amount);
                    break;
            }

            if (cmd == null) return;
            _macroBuffer.Add(cmd);
            RefreshMacroList();
            SetStatus("Добавлено в макрос: " + cmd.Description, true);
        }

        private void OnRunMacro(bool immediate)
        {
            if (_macroBuffer.Count == 0)
            { SetStatus("Макрос пуст — добавьте операции.", false); return; }

            var name = txtMacroName.Text.Trim();
            if (string.IsNullOrEmpty(name)) name = "Макро";

            var macro = new MacroCommand(name, new List<BankCommand>(_macroBuffer));
            _macroBuffer.Clear();
            RefreshMacroList();

            if (immediate)
            {
                var r = _terminal.ExecuteNow(macro);
                RefreshAll();
                SetStatus(r.Message, r.IsSuccess);
            }
            else
            {
                _terminal.Enqueue(macro);
                RefreshAll();
                SetStatus("Макрос добавлен в очередь: " + macro.Description, true);
            }
        }

        private void RefreshAll()
        {
            RefreshAccountsGrid();
            RefreshQueue();
            RefreshLog();
            btnUndo.Enabled = _terminal.CanUndo;
            btnRedo.Enabled = _terminal.CanRedo;
            btnFlush.Enabled = _terminal.QueueCount > 0;
        }

        private void RefreshAccountsGrid()
        {
            dgvAccounts.Rows.Clear();
            foreach (var a in _accounts)
            {
                int i = dgvAccounts.Rows.Add(a.Number, a.Owner,
                    string.Format("{0:N2} руб.", a.Balance));
                dgvAccounts.Rows[i].DefaultCellStyle.BackColor =
                    a.Balance < 5000 ? Color.FromArgb(255, 235, 235) : Color.White;
            }
        }

        private void RefreshAccountCombos()
        {
            cmbFrom.Items.Clear();
            cmbTo.Items.Clear();
            foreach (var a in _accounts)
            {
                cmbFrom.Items.Add(a);
                cmbTo.Items.Add(a);
            }
            if (cmbFrom.Items.Count > 0) cmbFrom.SelectedIndex = 0;
            if (cmbTo.Items.Count > 1) cmbTo.SelectedIndex = 1;
        }

        private void UpdateOperationUI()
        {
            bool isTransfer = cmbType.SelectedIndex == 2;
            lblTo.Visible = isTransfer;
            cmbTo.Visible = isTransfer;
        }

        private void RefreshMacroList()
        {
            lstMacroOps.Items.Clear();
            foreach (var cmd in _macroBuffer)
                lstMacroOps.Items.Add(cmd.Description);
            btnRunMacro.Enabled = _macroBuffer.Count > 0;
            btnQueueMacro.Enabled = _macroBuffer.Count > 0;
        }

        private void RefreshQueue()
        {
            lstQueue.Items.Clear();
            foreach (var cmd in _terminal.PeekQueue())
                lstQueue.Items.Add("[" + cmd.Type + "]  " + cmd.Description);
        }

        private void RefreshLog()
        {
            lvLog.Items.Clear();
            foreach (var e in _terminal.Log)
            {
                var item = new ListViewItem(e.Time.ToString("yyyy-MM-dd HH:mm:ss"));
                item.SubItems.Add(e.Status);
                item.SubItems.Add(e.Description);
                item.ForeColor = e.Status.StartsWith("ОШИБКА") ? Color.Crimson :
                                 e.Status == "ОТМЕНА" ? Color.OrangeRed :
                                 e.Status == "ОЧЕРЕДЬ" ? Color.SteelBlue : Color.Black;
                lvLog.Items.Add(item);
            }
            if (lvLog.Items.Count > 0)
                lvLog.EnsureVisible(lvLog.Items.Count - 1);
        }

        private void SetStatus(string message, bool ok)
        {
            lblStatus.Text = (ok ? "OK  " : "Ошибка  ") + message.Replace("\n", "  ");
            lblStatus.ForeColor = ok ? Color.FromArgb(30, 140, 60) : Color.Crimson;
        }

        private static GroupBox MakeGroupBox(string title, int x, int y, int w, int h)
        {
            return new GroupBox
            {
                Text = title,
                Left = x,
                Top = y,
                Width = w,
                Height = h,
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                ForeColor = Color.FromArgb(50, 80, 130)
            };
        }

        private static Button MakeButton(string text, int x, int y, int w, Color color)
        {
            var btn = new Button
            {
                Text = text,
                Left = x,
                Top = y,
                Width = w,
                Height = 28,
                BackColor = color,
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 8.5f),
                Cursor = Cursors.Hand,
                UseVisualStyleBackColor = false
            };
            btn.FlatAppearance.BorderColor = color;
            btn.Paint += (s, e) =>
            {
                var b = (Button)s;
                var bc = b.Enabled ? color : Color.FromArgb(180, color.R, color.G, color.B);
                e.Graphics.Clear(bc);
                TextRenderer.DrawText(e.Graphics, b.Text, b.Font,
                    e.ClipRectangle, Color.White,
                    TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
            };
            return btn;
        }

        private static ListView MakeListView()
        {
            return new ListView
            {
                Dock = DockStyle.Fill,
                View = View.Details,
                FullRowSelect = true,
                GridLines = true,
                Font = new Font("Consolas", 8.5f)
            };
        }
    }
}
