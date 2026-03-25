#include "httplib.h"
#include <iostream>
#include <string>
#include <vector>
#include <unordered_map>
#include <sstream>
#include <algorithm>
#include <fstream>
#include <cstdint>
#include <mutex>

using namespace std;

class ColorSpace {
public:
    static const int RGB = 0;
    static const int GRAYSCALE = 1;
};

class ImageSize {
public:
    int width;
    int height;
    ImageSize() : width(0), height(0) {}
    ImageSize(int w, int h) : width(w), height(h) {}
};

class Pixel {
public:
    uint8_t r, g, b;
    Pixel() : r(0), g(0), b(0) {}
    Pixel(uint8_t red, uint8_t green, uint8_t blue) : r(red), g(green), b(blue) {}
};

class Preset {
public:
    string name;
    int target_colorspace;
    ImageSize target_size;
    vector<string> filters;

    Preset() : target_colorspace(ColorSpace::RGB) {}
    Preset(const string& n, int cs, ImageSize ts, const vector<string>& f)
        : name(n), target_colorspace(cs), target_size(ts), filters(f) {}
};

class BmpHandler {
public:
    vector<uint8_t> exportBmp(const vector<Pixel>& pixels, const ImageSize& size) const {
        vector<uint8_t> bmp;
        if (pixels.empty() || size.width <= 0 || size.height <= 0) {
            static const uint8_t ph[] = {
                0x42, 0x4D, 0x3A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x36, 0x00,
                0x00, 0x00, 0x28, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00,
                0x00, 0x00, 0x01, 0x00, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0x00, 0x00
            };
            return vector<uint8_t>(ph, ph + sizeof(ph));
        }
        int row_size = (size.width * 3 + 3) & ~3;
        int pixel_data_size = row_size * size.height;
        int file_size = 54 + pixel_data_size;

        bmp.push_back('B'); bmp.push_back('M');
        for (int i = 0; i < 4; i++) bmp.push_back((file_size >> (i * 8)) & 0xFF);
        bmp.push_back(0); bmp.push_back(0); bmp.push_back(0); bmp.push_back(0);
        bmp.push_back(54); bmp.push_back(0); bmp.push_back(0); bmp.push_back(0);
        bmp.push_back(40); bmp.push_back(0); bmp.push_back(0); bmp.push_back(0);
        for (int i = 0; i < 4; i++) bmp.push_back((size.width >> (i * 8)) & 0xFF);
        for (int i = 0; i < 4; i++) bmp.push_back((size.height >> (i * 8)) & 0xFF);
        bmp.push_back(1); bmp.push_back(0);
        bmp.push_back(24); bmp.push_back(0);
        for (int i = 0; i < 4; i++) bmp.push_back(0);
        for (int i = 0; i < 4; i++) bmp.push_back((pixel_data_size >> (i * 8)) & 0xFF);
        for (int i = 0; i < 16; i++) bmp.push_back(0);

        for (int y = size.height - 1; y >= 0; --y) {
            for (int x = 0; x < size.width; ++x) {
                const Pixel& p = pixels[y * size.width + x];
                bmp.push_back(p.b); bmp.push_back(p.g); bmp.push_back(p.r);
            }
            for (int i = 0; i < row_size - size.width * 3; ++i) bmp.push_back(0);
        }
        return bmp;
    }

    bool loadBmp(const string& path, vector<Pixel>& pixels, ImageSize& size) {
        ifstream file(path, ios::binary);
        if (!file) return false;
        uint8_t header[54];
        file.read(reinterpret_cast<char*>(header), 54);
        if (header[0] != 'B' || header[1] != 'M') return false;
        size.width = *reinterpret_cast<uint32_t*>(&header[18]);
        size.height = *reinterpret_cast<uint32_t*>(&header[22]);
        if (*reinterpret_cast<uint16_t*>(&header[28]) != 24) return false;
        file.seekg(54);
        pixels.resize(size.width * size.height);
        int row_size = (size.width * 3 + 3) & ~3;
        vector<uint8_t> row_buffer(row_size);
        for (int y = size.height - 1; y >= 0; --y) {
            file.read(reinterpret_cast<char*>(row_buffer.data()), row_size);
            for (int x = 0; x < size.width; ++x) {
                int idx = y * size.width + x;
                pixels[idx] = Pixel(row_buffer[x * 3 + 2], row_buffer[x * 3 + 1], row_buffer[x * 3]);
            }
        }
        return true;
    }

    bool saveBmp(const string& path, const vector<Pixel>& pixels, const ImageSize& size) {
        vector<uint8_t> bmp = exportBmp(pixels, size);
        ofstream file(path, ios::binary);
        if (!file) return false;
        file.write(reinterpret_cast<char*>(bmp.data()), bmp.size());
        return true;
    }
};

class ImageLoader {
private:
    BmpHandler bmpHandler;
public:
    bool load(const string& path, vector<Pixel>& pixels, ImageSize& size) {
        if (path == "demo.bmp") return generateDemo(pixels, size);
        return bmpHandler.loadBmp(path, pixels, size);
    }
    bool generateDemo(vector<Pixel>& pixels, ImageSize& size) {
        size = ImageSize(800, 600);
        pixels.resize(size.width * size.height);
        for (int y = 0; y < size.height; ++y) {
            for (int x = 0; x < size.width; ++x) {
                int idx = y * size.width + x;
                pixels[idx] = Pixel(
                    static_cast<uint8_t>((x * 255) / size.width),
                    static_cast<uint8_t>((y * 255) / size.height),
                    static_cast<uint8_t>(128 + (x - y) / 4)
                );
            }
        }
        return true;
    }
};

class ColorProcessor {
public:
    bool toGrayscale(vector<Pixel>& pixels) {
        for (auto& p : pixels) {
            uint8_t g = static_cast<uint8_t>(0.299f * p.r + 0.587f * p.g + 0.114f * p.b);
            p.r = p.g = p.b = g;
        }
        return true;
    }
};

class FilterEngine {
public:
    bool apply(const string& name, vector<Pixel>& pixels) {
        if (name == "grayscale") applyGrayscale(pixels);
        else if (name == "sepia") applySepia(pixels);
        else if (name == "brightness") applyBrightness(pixels);
        else if (name == "contrast") applyContrast(pixels);
        else if (name == "invert") applyInvert(pixels);
        else return false;
        return true;
    }
    vector<string> getAvailableFilters() const {
        return { "grayscale", "sepia", "brightness", "contrast", "invert" };
    }
private:
    void applyGrayscale(vector<Pixel>& p) {
        for (auto& px : p) {
            uint8_t g = static_cast<uint8_t>(0.299f * px.r + 0.587f * px.g + 0.114f * px.b);
            px.r = px.g = px.b = g;
        }
    }
    void applySepia(vector<Pixel>& p) {
        for (auto& px : p) {
            int r = px.r, g = px.g, b = px.b;
            int sr = min(255, static_cast<int>(r * 0.393f + g * 0.769f + b * 0.189f));
            int sg = min(255, static_cast<int>(r * 0.349f + g * 0.686f + b * 0.168f));
            int sb = min(255, static_cast<int>(r * 0.272f + g * 0.534f + b * 0.131f));
            px.r = static_cast<uint8_t>(sr);
            px.g = static_cast<uint8_t>(sg);
            px.b = static_cast<uint8_t>(sb);
        }
    }
    void applyBrightness(vector<Pixel>& p) {
        int offset = 50;
        for (auto& px : p) {
            px.r = static_cast<uint8_t>(min(255, max(0, static_cast<int>(px.r) + offset)));
            px.g = static_cast<uint8_t>(min(255, max(0, static_cast<int>(px.g) + offset)));
            px.b = static_cast<uint8_t>(min(255, max(0, static_cast<int>(px.b) + offset)));
        }
    }
    void applyContrast(vector<Pixel>& p) {
        float factor = 1.5f;
        for (auto& px : p) {
            px.r = static_cast<uint8_t>(min(255, max(0, static_cast<int>((px.r - 128) * factor + 128))));
            px.g = static_cast<uint8_t>(min(255, max(0, static_cast<int>((px.g - 128) * factor + 128))));
            px.b = static_cast<uint8_t>(min(255, max(0, static_cast<int>((px.b - 128) * factor + 128))));
        }
    }
    void applyInvert(vector<Pixel>& p) {
        for (auto& px : p) {
            px.r = 255 - px.r;
            px.g = 255 - px.g;
            px.b = 255 - px.b;
        }
    }
};

class Resizer {
public:
    void resize(vector<Pixel>& pixels, ImageSize& current, ImageSize target) {
        if (target.width <= 0 || target.height <= 0) return;
        vector<Pixel> new_pixels(target.width * target.height);
        for (int y = 0; y < target.height; ++y) {
            for (int x = 0; x < target.width; ++x) {
                int src_y = y * current.height / target.height;
                int src_x = x * current.width / target.width;
                new_pixels[y * target.width + x] = pixels[src_y * current.width + src_x];
            }
        }
        pixels = move(new_pixels);
        current = target;
    }
};

class PhotoLabServer {
private:
    httplib::Server server;
    mutex processingMutex;

    ImageLoader loader;
    FilterEngine filters;
    Resizer resizer;
    BmpHandler bmpHandler;

    bool loaded = false;
    ImageSize currentSize;
    ImageSize originalSize;
    vector<Pixel> pixelBuffer;
    vector<Pixel> originalPixels;
    unordered_map<string, Preset> presets;
    int filtersCount = 0;

    string jsonInfo() {
        ostringstream ss;
        ss << "{\"width\":" << currentSize.width
            << ",\"height\":" << currentSize.height
            << ",\"loaded\":" << (loaded ? "true" : "false")
            << ",\"filters\":" << filtersCount << "}";
        return ss.str();
    }

    void setCors(httplib::Response& res) {
        res.set_header("Access-Control-Allow-Origin", "*");
        res.set_header("Content-Type", "application/json; charset=utf-8");
    }

    string loadHtmlFile(const string& /* path */) {
        ifstream file("D:/Uni/TSU/OOAP2/lab2/PhotoLab/index.html");
        if (file) {
            stringstream buffer;
            buffer << file.rdbuf();
            return buffer.str();
        }
        return "<h1>index.html not found!</h1>";
    }

    bool loadImage(const string& path) {
        if (!loader.load(path, pixelBuffer, currentSize)) return false;
        originalPixels = pixelBuffer;
        originalSize = currentSize;
        filtersCount = 0;
        loaded = true;
        return true;
    }

    bool saveImage(const string& path) {
        if (!loaded) return false;
        return bmpHandler.saveBmp(path, pixelBuffer, currentSize);
    }

    bool applyPreset(const string& name) {
        if (!loaded) return false;
        auto it = presets.find(name);
        if (it == presets.end()) return false;

        pixelBuffer = originalPixels;
        currentSize = originalSize;

        for (const auto& f : it->second.filters) {
            filters.apply(f, pixelBuffer);
            filtersCount++;
        }

        if (it->second.target_colorspace != ColorSpace::RGB) {
            ColorProcessor cp;
            cp.toGrayscale(pixelBuffer);
            filtersCount++;
        }

        if (it->second.target_size.width > 0) {
            ImageSize target = it->second.target_size;
            if (target.height == 0) {
                target.height = currentSize.height * target.width / currentSize.width;
            }
            resizer.resize(pixelBuffer, currentSize, target);
        }
        return true;
    }

    bool applyFilter(const string& name) {
        if (!loaded) return false;
        if (filters.apply(name, pixelBuffer)) {
            filtersCount++;
            return true;
        }
        return false;
    }

    bool resetImage() {
        if (!loaded) return false;
        pixelBuffer = originalPixels;
        currentSize = originalSize;
        filtersCount = 0;
        return true;
    }

    vector<uint8_t> getPreviewBmp() const {
        if (!loaded || pixelBuffer.empty()) {
            return bmpHandler.exportBmp({}, ImageSize(0, 0));
        }
        return bmpHandler.exportBmp(pixelBuffer, currentSize);
    }

    void initPresets() {
        presets["web_ready"] = Preset("web_ready", ColorSpace::RGB, ImageSize(1920, 0), { "contrast" });
        presets["bw_artistic"] = Preset("bw_artistic", ColorSpace::GRAYSCALE, ImageSize(0, 0), { "grayscale", "contrast" });
        presets["instagram"] = Preset("instagram", ColorSpace::RGB, ImageSize(1080, 1080), { "contrast" });
    }

    void handleGet(const httplib::Request&, httplib::Response& res) {
        string html = loadHtmlFile("index.html");
        res.set_content(html, "text/html; charset=utf-8");
    }

    void handlePreview(const httplib::Request&, httplib::Response& res) {
        lock_guard<mutex> lock(processingMutex);
        auto bmp = getPreviewBmp();
        res.set_header("Content-Type", "image/bmp");
        res.set_header("Cache-Control", "no-cache");
        res.set_content(reinterpret_cast<const char*>(bmp.data()), bmp.size(), "image/bmp");
    }

    void handleUpload(const httplib::Request& req, httplib::Response& res) {
        lock_guard<mutex> lock(processingMutex);
        setCors(res);
        if (req.body.empty()) {
            res.set_content("{\"ok\":false,\"error\":\"Empty\"}", "application/json");
            return;
        }
        ofstream out("temp_upload.bmp", ios::binary);
        out.write(req.body.data(), req.body.size());
        out.close();
        if (loadImage("temp_upload.bmp")) {
            remove("temp_upload.bmp");
            res.set_content("{\"ok\":true,\"info\":" + jsonInfo() + "}", "application/json");
        } else {
            remove("temp_upload.bmp");
            res.set_content("{\"ok\":false,\"error\":\"Invalid BMP\"}", "application/json");
        }
    }

    void handleLoad(const httplib::Request& req, httplib::Response& res) {
        lock_guard<mutex> lock(processingMutex);
        setCors(res);
        string path = req.body;
        if (!path.empty() && path.front() == '"') path = path.substr(1);
        if (!path.empty() && path.back() == '"') path.pop_back();
        if (loadImage(path)) {
            res.set_content("{\"ok\":true,\"info\":" + jsonInfo() + "}", "application/json");
        } else {
            res.set_content("{\"ok\":false,\"error\":\"Load failed\"}", "application/json");
        }
    }

    void handlePreset(const httplib::Request& req, httplib::Response& res) {
        lock_guard<mutex> lock(processingMutex);
        setCors(res);
        string preset = "web_ready";
        auto pos = req.body.find("\"preset\"");
        if (pos != string::npos) {
            auto q1 = req.body.find('"', pos + 8);
            auto q2 = req.body.find('"', q1 + 1);
            if (q1 != string::npos && q2 != string::npos) {
                preset = req.body.substr(q1 + 1, q2 - q1 - 1);
            }
        }
        if (applyPreset(preset)) {
            res.set_content("{\"ok\":true,\"filters\":" + to_string(filtersCount) + "}", "application/json");
        } else {
            res.set_content("{\"ok\":false,\"error\":\"Preset failed\"}", "application/json");
        }
    }

    void handleFilter(const httplib::Request& req, httplib::Response& res) {
        lock_guard<mutex> lock(processingMutex);
        setCors(res);
        string name = "grayscale";
        auto pos = req.body.find("\"name\"");
        if (pos != string::npos) {
            auto q1 = req.body.find('"', pos + 6);
            auto q2 = req.body.find('"', q1 + 1);
            if (q1 != string::npos && q2 != string::npos) {
                name = req.body.substr(q1 + 1, q2 - q1 - 1);
            }
        }
        if (applyFilter(name)) {
            res.set_content("{\"ok\":true,\"filters\":" + to_string(filtersCount) + "}", "application/json");
        } else {
            res.set_content("{\"ok\":false,\"error\":\"Filter failed\"}", "application/json");
        }
    }

    void handleReset(const httplib::Request&, httplib::Response& res) {
        lock_guard<mutex> lock(processingMutex);
        setCors(res);
        if (resetImage()) {
            res.set_content("{\"ok\":true,\"filters\":0}", "application/json");
        } else {
            res.set_content("{\"ok\":false,\"error\":\"Reset failed\"}", "application/json");
        }
    }

    void handleSave(const httplib::Request&, httplib::Response& res) {
        lock_guard<mutex> lock(processingMutex);
        setCors(res);
        if (saveImage("output.bmp")) {
            res.set_content("{\"ok\":true,\"path\":\"output.bmp\"}", "application/json");
        } else {
            res.set_content("{\"ok\":false,\"error\":\"Save failed\"}", "application/json");
        }
    }

    void handleFilters(const httplib::Request&, httplib::Response& res) {
        setCors(res);
        auto f = filters.getAvailableFilters();
        string j = "[";
        for (size_t i = 0; i < f.size(); ++i) {
            j += "\"" + f[i] + "\"";
            if (i + 1 < f.size()) j += ",";
        }
        j += "]";
        res.set_content(j, "application/json");
    }

public:
    PhotoLabServer() {
        initPresets();
        server.Get("/", [this](auto& r, auto& res) { handleGet(r, res); });
        server.Get("/preview", [this](auto& r, auto& res) { handlePreview(r, res); });
        server.Post("/upload", [this](auto& r, auto& res) { handleUpload(r, res); });
        server.Post("/load", [this](auto& r, auto& res) { handleLoad(r, res); });
        server.Post("/preset", [this](auto& r, auto& res) { handlePreset(r, res); });
        server.Post("/filter", [this](auto& r, auto& res) { handleFilter(r, res); });
        server.Post("/reset", [this](auto& r, auto& res) { handleReset(r, res); });
        server.Post("/save", [this](auto& r, auto& res) { handleSave(r, res); });
        server.Get("/filters", [this](auto& r, auto& res) { handleFilters(r, res); });
        server.Options(".*", [](const httplib::Request&, httplib::Response& res) {
            res.set_header("Access-Control-Allow-Origin", "*");
            res.set_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            res.set_header("Access-Control-Allow-Headers", "Content-Type");
            res.status = 204;
        });
    }

    void run(const string& host, int port) {
        cout << "http://localhost:" << port << endl;
        server.listen(host.c_str(), port);
    }
};

int main() {
    PhotoLabServer app;
    app.run("0.0.0.0", 3000);
    return 0;
}