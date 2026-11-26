/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import UIKit
import UniformTypeIdentifiers

/// "Share to Jami" from other apps (I8).
///
/// Unlike jami-client-ios, whose extension starts a daemon of its own to send right away, this
/// one only hands the items over: it copies them into the App Group's share inbox and opens
/// Jami, where the share picker (the same screen Android's ACTION_SEND opens) chooses the
/// conversation. A second daemon in an extension's ~120 MB would also have to share the
/// account with the app's daemon, which is exactly what the App Group handshake of the
/// notification extension still has to solve.
///
/// The inbox layout is read by `IOSShareInbox.kt`: `ShareInbox/<id>/manifest.plist` with a
/// `text` string and a `files` array of names in the same folder.
final class ShareViewController: UIViewController {

    private let label = UILabel()
    private let spinner = UIActivityIndicatorView(style: .large)

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        label.text = NSLocalizedString("share_preparing", comment: "Shown while shared items are copied")
        label.numberOfLines = 0
        label.textAlignment = .center
        label.font = .preferredFont(forTextStyle: .body)
        label.adjustsFontForContentSizeCategory = true
        spinner.startAnimating()
        let stack = UIStackView(arrangedSubviews: [spinner, label])
        stack.axis = .vertical
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            stack.leadingAnchor.constraint(equalTo: view.layoutMarginsGuide.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: view.layoutMarginsGuide.trailingAnchor),
        ])
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        Task { await handOver() }
    }

    private func handOver() async {
        guard let inbox = ShareInbox.newEntry() else {
            finish(message: NSLocalizedString("share_unavailable", comment: "No shared container"), opened: false)
            return
        }
        let providers = (extensionContext?.inputItems as? [NSExtensionItem] ?? [])
            .flatMap { $0.attachments ?? [] }
        var texts: [String] = []
        var files: [String] = []
        for provider in providers {
            if let name = await copyFile(from: provider, into: inbox) {
                files.append(name)
            } else if let text = await loadText(from: provider) {
                texts.append(text)
            }
        }
        guard !texts.isEmpty || !files.isEmpty else {
            ShareInbox.discard(inbox)
            finish(message: NSLocalizedString("share_nothing", comment: "Nothing usable was shared"), opened: false)
            return
        }
        ShareInbox.writeManifest(in: inbox, text: texts.joined(separator: "\n"), files: files)
        let opened = await openJami()
        finish(message: NSLocalizedString("share_open_jami", comment: "Open Jami to pick the conversation"), opened: opened)
    }

    /// Files, images and movies: copied out of the provider, which deletes its copy afterwards.
    private func copyFile(from provider: NSItemProvider, into folder: URL) async -> String? {
        // Plain text and web links are not files, even though they conform to public.data.
        if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) ||
            (provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) &&
             !provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier)) {
            return nil
        }
        let types = [UTType.image, .movie, .audio, .pdf, .item]
        guard let type = types.first(where: { provider.hasItemConformingToTypeIdentifier($0.identifier) }) else {
            return nil
        }
        return await withCheckedContinuation { continuation in
            _ = provider.loadFileRepresentation(forTypeIdentifier: type.identifier) { url, _ in
                guard let url else { continuation.resume(returning: nil); return }
                let name = ShareInbox.uniqueName(url.lastPathComponent, in: folder)
                do {
                    try FileManager.default.copyItem(at: url, to: folder.appendingPathComponent(name))
                    continuation.resume(returning: name)
                } catch {
                    continuation.resume(returning: nil)
                }
            }
        }
    }

    /// Text and web links become the message text.
    private func loadText(from provider: NSItemProvider) async -> String? {
        for type in [UTType.url, .plainText] where provider.hasItemConformingToTypeIdentifier(type.identifier) {
            let item = try? await provider.loadItem(forTypeIdentifier: type.identifier)
            if let url = item as? URL { return url.absoluteString }
            if let text = item as? String { return text }
            if let data = item as? Data, let text = String(data: data, encoding: .utf8) { return text }
        }
        return nil
    }

    /// An extension has no UIApplication of its own; the host app's is found up the responder
    /// chain, which is how share extensions open their app.
    private func openJami() async -> Bool {
        guard let url = URL(string: "\(ShareInbox.urlScheme)://share") else { return false }
        var responder: UIResponder? = self
        while let current = responder {
            if let application = current as? UIApplication {
                return await application.open(url)
            }
            responder = current.next
        }
        return false
    }

    private func finish(message: String, opened: Bool) {
        spinner.stopAnimating()
        label.text = message
        // Opened: Jami is in front already. Otherwise leave the message up for a moment.
        let delay: TimeInterval = opened ? 0 : 2
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            self?.extensionContext?.completeRequest(returningItems: nil)
        }
    }
}

/// The share inbox in the App Group container; the app reads it in `IOSShareInbox.kt`.
enum ShareInbox {
    static let appGroup = "group.net.jami.kmp"
    static let urlScheme = "jamikmp"

    static func newEntry() -> URL? {
        guard let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup) else {
            return nil
        }
        let entry = container.appendingPathComponent("ShareInbox", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: entry, withIntermediateDirectories: true)
            return entry
        } catch {
            return nil
        }
    }

    static func uniqueName(_ name: String, in folder: URL) -> String {
        let base = (name as NSString).deletingPathExtension
        let ext = (name as NSString).pathExtension
        var candidate = name.isEmpty ? "file" : name
        var index = 1
        while FileManager.default.fileExists(atPath: folder.appendingPathComponent(candidate).path) {
            candidate = ext.isEmpty ? "\(base)-\(index)" : "\(base)-\(index).\(ext)"
            index += 1
        }
        return candidate
    }

    /// Written last: the app takes an entry only once its manifest exists.
    static func writeManifest(in folder: URL, text: String, files: [String]) {
        let manifest: [String: Any] = ["text": text, "files": files]
        (manifest as NSDictionary).write(to: folder.appendingPathComponent("manifest.plist"), atomically: true)
    }

    static func discard(_ folder: URL) {
        try? FileManager.default.removeItem(at: folder)
    }
}
