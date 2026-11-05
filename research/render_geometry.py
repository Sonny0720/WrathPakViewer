#!/usr/bin/env python3
"""Render exact OBJ triangles in three views (pip install matplotlib numpy).

Only the display axes change from source Y-up to plot Z-up. OBJ data is unchanged.
"""
import argparse
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
import numpy as np
from audit_obj import load


def render(paths, output):
    figure = plt.figure(figsize=(13, 4*len(paths)))
    for row, path in enumerate(paths):
        vertices, faces = load(path)
        vertices = np.array(vertices)[:, [0, 2, 1]]
        vertices[:, 1] *= -1  # Right-handed Y-up to Z-up display transform.
        polygons = vertices[np.array(faces)]
        used = polygons.reshape(-1, 3)
        low, high = used.min(0), used.max(0)
        center, radius = (low+high)/2, max(high-low)*.53
        for col, (elevation, azimuth, label) in enumerate(((0, -90, 'Front'), (0, 0, 'Side'), (25, -60, 'Perspective'))):
            axes = figure.add_subplot(len(paths), 3, row*3+col+1, projection='3d')
            normal = np.cross(polygons[:, 1]-polygons[:, 0], polygons[:, 2]-polygons[:, 0])
            length = np.linalg.norm(normal, axis=1)
            normal /= np.maximum(length[:, None], 1e-12)
            el, az = np.deg2rad([elevation, azimuth])
            camera = np.array([np.cos(el)*np.cos(az), np.cos(el)*np.sin(az), np.sin(el)])
            visible = (normal @ camera) > 0
            light = camera + np.array([-.3, -.2, .7])
            light /= np.linalg.norm(light)
            brightness = .28 + .72*np.clip(normal @ light, 0, 1)
            colors = np.clip(brightness[:, None]*np.array([.70, .78, .84]), 0, 1)
            axes.add_collection3d(Poly3DCollection(polygons[visible], facecolors=colors[visible],
                                                  edgecolors='none', linewidth=0))
            axes.set_proj_type('ortho')
            axes.set(xlim=(center[0]-radius, center[0]+radius),
                     ylim=(center[1]-radius, center[1]+radius),
                     zlim=(center[2]-radius, center[2]+radius))
            axes.set_box_aspect((1, 1, 1))
            axes.view_init(elevation, azimuth)
            axes.set_title(f'{path.parent.name.capitalize()} · {label}')
            axes.set_axis_off()
    figure.tight_layout()
    output.parent.mkdir(parents=True, exist_ok=True)
    figure.savefig(output, dpi=160)
    plt.close(figure)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('obj', type=Path, nargs='+')
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    render(args.obj, args.out)
