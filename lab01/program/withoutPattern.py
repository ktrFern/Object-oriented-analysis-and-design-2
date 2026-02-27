import sys
from PyQt6.QtWidgets import QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton, QListWidget, QMessageBox, QScrollArea, QGridLayout, QDialog, QCheckBox
from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtGui import QPixmap

REGIONS = {
    "DE": {"tax_percent": 19, "currency": lambda a: f"{a:.2f} €", "delivery_options": {"DHL": 5, "Hermes": 4, "DPD": 6}, "promotion_percent": 5},
    "US": {"tax_percent": 7, "currency": lambda a: f"${a:.2f}", "delivery_options": {"FedEx": 10, "UPS": 9, "USPS": 6}, "promotion_percent": 10},
    "JP": {"tax_percent": 10, "currency": lambda a: f"¥{a:.0f}", "delivery_options": {"Yu-Pack": 800, "Kuroneko": 1200, "Sagawa": 1000}, "promotion_percent": 8}
}

class OrderService:
    def __init__(self, region_key):
        self.set_region(region_key)

    def set_region(self, region_key):
        self.region_key = region_key
        self.config = REGIONS[region_key]

    def format_price(self, amount):
        return self.config["currency"](amount)

    def calculate_subtotal(self, cart):
        return sum(price for _, price in cart)

    def apply_tax(self, amount):
        return amount * (1 + self.config["tax_percent"] / 100)

    def apply_discount(self, amount):
        return amount * (self.config["promotion_percent"] / 100)

    def get_delivery_options(self):
        return self.config["delivery_options"]

    def get_promo_percent(self):
        return self.config["promotion_percent"]

class ProductCard(QWidget):
    def __init__(self, name, price, image_path, add_callback, remove_callback, order_service):
        super().__init__()
        self.name = name
        self.price = price
        self.add_callback = add_callback
        self.remove_callback = remove_callback
        self.order_service = order_service
        self.init_ui(image_path)

    def init_ui(self, image_path):
        layout = QVBoxLayout()
        layout.setAlignment(Qt.AlignmentFlag.AlignCenter)
        pixmap = QPixmap(image_path).scaled(150, 100, Qt.AspectRatioMode.KeepAspectRatio)
        img = QLabel()
        img.setPixmap(pixmap)

        layout.addWidget(img)
        layout.addWidget(QLabel(self.name))
        layout.addWidget(QLabel(self.order_service.format_price(self.price)))

        btns = QHBoxLayout()
        add_btn = QPushButton("+")
        rem_btn = QPushButton("-")
        btns.addWidget(add_btn)
        btns.addWidget(rem_btn)
        layout.addLayout(btns)

        add_btn.clicked.connect(lambda: self.add_callback((self.name, self.price)))
        rem_btn.clicked.connect(lambda: self.remove_callback((self.name, self.price)))

        self.setLayout(layout)
        self.setStyleSheet(
            "QWidget{background:#f5f5f5; border:1px solid #ccc; border-radius:8px; margin:5px;}"
            "QPushButton{background:#4CAF50; color:white; border-radius:5px; padding:4px;}"
            "QPushButton:hover{background:#45a049;}"
        )

class CheckoutWidget(QWidget):
    confirmed = pyqtSignal(dict)
    def __init__(self, order_service, cart, parent=None):
        super().__init__(parent)
        self.order_service = order_service
        self.cart = cart
        self.init_ui()

    def init_ui(self):
        self.layout = QVBoxLayout(self)
        self.layout.addWidget(QLabel("Способ доставки"))

        self.delivery_buttons = {}
        self.delivery_layout = QHBoxLayout()
        self.layout.addLayout(self.delivery_layout)

        self.discount_box = QCheckBox()
        self.layout.addWidget(self.discount_box)

        self.total_label = QLabel()
        self.total_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.total_label.setStyleSheet("font-size:16px;")
        self.layout.addWidget(self.total_label)

        confirm = QPushButton("Оформить заказ")
        self.layout.addWidget(confirm)
        confirm.clicked.connect(self.confirm_order)
        self.discount_box.stateChanged.connect(self.recalculate)

        self.refresh_region()

    def refresh_region(self):
        while self.delivery_layout.count():
            widget = self.delivery_layout.takeAt(0).widget()
            if widget:
                widget.deleteLater()

        self.delivery_buttons.clear()
        self.delivery_options = self.order_service.get_delivery_options()

        for name, price in self.delivery_options.items():
            btn = QPushButton(f"🚚 {name}\n{self.order_service.format_price(price)}")
            btn.setCheckable(True)
            btn.clicked.connect(self.delivery_select)
            btn.setStyleSheet(
                "QPushButton{border:1px solid #ccc;border-radius:10px;padding:10px;background:#fafafa;color:#222;min-width:70px;}"
                "QPushButton:hover{background:#f0f0f0;}"
                "QPushButton:checked{border:2px solid #2E7D32;background:#E8F5E9;color:#1B5E20;font-weight:bold;}"
            )
            self.delivery_buttons[name] = btn
            self.delivery_layout.addWidget(btn)

        if self.delivery_buttons:
            next(iter(self.delivery_buttons.values())).setChecked(True)

        self.discount_box.setText(f"Применить скидку ({self.order_service.get_promo_percent()}%)")
        self.discount_box.setChecked(False)
        self.recalculate()

    def delivery_select(self):
        sender = self.sender()
        for btn in self.delivery_buttons.values():
            if btn is not sender:
                btn.setChecked(False)
        self.recalculate()

    def get_selected_delivery(self):
        for name, btn in self.delivery_buttons.items():
            if btn.isChecked():
                return name
        return next(iter(self.delivery_buttons))

    def recalculate(self):
        subtotal = self.order_service.calculate_subtotal(self.cart)
        discount = self.order_service.apply_discount(subtotal) if self.discount_box.isChecked() else 0
        after_discount = subtotal - discount
        delivery = self.delivery_options[self.get_selected_delivery()] if self.cart else 0
        taxable_amount = after_discount + delivery
        total_with_tax = self.order_service.apply_tax(taxable_amount)
        tax = total_with_tax - taxable_amount

        self.receipt = {"subtotal": subtotal, "discount": discount, "tax": tax, "delivery": delivery, "total": total_with_tax}
        tax_percent = round((tax / taxable_amount) * 100) if taxable_amount else 0

        self.total_label.setText(
            f"<div style='text-align:center;line-height:1.4;'>"
            f"<div style='font-size:14px;color:#666;'>Итого к оплате</div>"
            f"<div style='font-size:22px;font-weight:600;color:#1B5E20;'>{self.order_service.format_price(total_with_tax)}</div>"
            f"<div style='font-size:12px;color:#888;'>включая НДС {tax_percent}% — {self.order_service.format_price(tax)}</div>"
            f"</div>"
        )

    def confirm_order(self):
        if not self.cart:
            QMessageBox.warning(self, "Ошибка", "Корзина пуста! Добавьте товары перед оформлением заказа.")
            return
        self.confirmed.emit(self.receipt)

class ReceiptDialog(QDialog):
    def __init__(self, receipt_text, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Чек")
        self.setMinimumSize(300, 400)
        layout = QVBoxLayout(self)
        label = QLabel(receipt_text)
        label.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        layout.addWidget(label)
        layout.addStretch()
        btn = QPushButton("Закрыть")
        btn.clicked.connect(self.accept)
        layout.addWidget(btn)

class ShopApp(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("🌍 International Shop")
        self.setMinimumSize(700, 600)

        self.products = [("Frog", 1000, "froge.png"), ("Fox", 200, "fox.png"),
                         ("Dog", 800, "doge.png"), ("Possum", 150, "possum.png"),
                         ("Cat", 400, "cat.png")]

        self.cart = []
        self.current_region = "🇩🇪"
        self.order_service = OrderService(self.current_region)
        self.init_ui()
        self.update_catalog()
        self.update_flag_styles()

    def init_ui(self):
        central = QWidget()
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)
        top = QHBoxLayout()
        self.flag_buttons = {}

        for flag in REGIONS:
            btn = QPushButton(flag)
            btn.setCheckable(True)
            btn.setStyleSheet(
                "QPushButton{border:1px solid #ccc;border-radius:10px;background:#fafafa;color:#222;font-size:16px;min-width:60px;}"
                "QPushButton:hover{background:#f0f0f0;}"
                "QPushButton:checked{border:2px solid #2E7D32;background:#E8F5E9;color:#1B5E20;font-weight:bold;}"
            )
            btn.clicked.connect(lambda _, f=flag: self.change_region(f))
            self.flag_buttons[flag] = btn
            top.addWidget(btn)

        top.addStretch()
        layout.addLayout(top)
        body = QHBoxLayout()
        layout.addLayout(body)

        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.catalog = QWidget()
        self.grid = QGridLayout(self.catalog)
        self.scroll.setWidget(self.catalog)
        body.addWidget(self.scroll, 3)

        side = QVBoxLayout()
        side.addWidget(QLabel("Корзина"))
        self.cart_list = QListWidget()
        side.addWidget(self.cart_list)
        self.result = QLabel("Корзина пуста")
        side.addWidget(self.result)

        self.checkout_widget = CheckoutWidget(self.order_service, self.cart, self)
        side.addWidget(self.checkout_widget)
        self.checkout_widget.confirmed.connect(self.finish_checkout)
        body.addLayout(side, 1)

    def update_flag_styles(self):
        for flag, btn in self.flag_buttons.items():
            btn.setChecked(flag == self.current_region)

    def change_region(self, flag):
        self.current_region = flag
        self.order_service.set_region(flag)
        self.checkout_widget.order_service = self.order_service
        self.checkout_widget.refresh_region()
        self.update_catalog()
        self.update_flag_styles()

    def update_catalog(self):
        while self.grid.count():
            self.grid.takeAt(0).widget().deleteLater()

        r = c = 0
        for p in self.products:
            self.grid.addWidget(ProductCard(*p, self.add_to_cart, self.remove_from_cart, self.order_service), r, c)
            c += 1
            if c == 3:
                c = 0
                r += 1
        self.update_cart()

    def add_to_cart(self, item):
        self.cart.append(item)
        self.update_cart()

    def remove_from_cart(self, item):
        if item in self.cart:
            self.cart.remove(item)
        self.update_cart()

    def update_cart(self):
        self.cart_list.clear()
        for n, p in self.cart:
            self.cart_list.addItem(f"{n} — {self.order_service.format_price(p)}")

        if self.cart:
            self.result.setText(f"Итого: {self.order_service.format_price(self.order_service.calculate_subtotal(self.cart))}")
        else:
            self.result.setText("Корзина пуста")

        self.checkout_widget.recalculate()

    def finish_checkout(self, r):
        if not self.cart:
            QMessageBox.warning(self, "Ошибка", "Корзина пуста!")
            return

        items_count = {}
        for name, price in self.cart:
            if name not in items_count:
                items_count[name] = {"qty": 0, "price": price}
            items_count[name]["qty"] += 1

        items_text = "".join(
            f"{name:12} {self.order_service.format_price(info['price'])} × {info['qty']} = "
            f"{self.order_service.format_price(info['price'] * info['qty'])}\n"
            for name, info in items_count.items()
        )

        discount_percent = self.order_service.promo.percent
        subtotal_str = self.order_service.format_price(r["subtotal"])
        discount_str = self.order_service.format_price(r["discount"])
        delivery_str = self.order_service.format_price(r["delivery"])
        tax_str = self.order_service.format_price(r["tax"])
        total_str = self.order_service.format_price(r["total"])

        taxable_amount = r["subtotal"] - r["discount"] + r["delivery"]
        tax_percent = round((r["tax"] / taxable_amount) * 100) if taxable_amount else 0

        receipt_text = (
            "ЧЕК\n"
            f"{items_text}"
            "----------------------\n"
            f"Подытог товаров: {subtotal_str}\n"
            f"Скидка ({discount_percent}%): {discount_str}\n"
            f"Доставка: {delivery_str}\n"
            f"НДС {tax_percent}%: {tax_str}\n"
            "\n"
            f"ИТОГО: {total_str}"
        )
        ReceiptDialog(receipt_text, self).exec()
        self.cart.clear()
        self.update_cart()

if __name__ == "__main__":
    app = QApplication(sys.argv)
    ShopApp().show()
    sys.exit(app.exec())